package com.cady.cadymarketing.feature.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.auth.AuthRepository
import com.cady.cadymarketing.data.backup.BackupRepository
import com.cady.cadymarketing.data.inventory.InventoryException
import com.cady.cadymarketing.data.inventory.InventoryRepository
import com.cady.cadymarketing.data.inventory.InventorySummary
import com.cady.cadymarketing.data.launch.LaunchInbox
import com.cady.cadymarketing.data.notify.BackgroundWork
import com.cady.cadymarketing.data.notify.Notifier
import com.cady.cadymarketing.data.settings.SettingsStore
import com.cady.cadymarketing.data.sync.SyncManager
import com.cady.cadymarketing.data.sync.SyncTrigger
import com.cady.cadymarketing.data.sync.SyncUiState
import com.cady.cadymarketing.data.team.RoleRepository
import com.cady.cadymarketing.domain.engine.BackupDecode
import com.cady.cadymarketing.domain.engine.BackupFile
import com.cady.cadymarketing.domain.engine.Feature
import com.cady.cadymarketing.domain.engine.GatewayClient
import com.cady.cadymarketing.domain.engine.GatewayConfig
import com.cady.cadymarketing.domain.engine.GatewayException
import com.cady.cadymarketing.domain.engine.Permissions
import com.cady.cadymarketing.domain.model.MktRole
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.File
import java.io.IOException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A backup the person picked, read and checked, waiting for her to confirm the restore. */
data class RestorePreview(val file: BackupFile, val skipped: Int)

data class SettingsUiState(
    val email: String? = null,
    val role: MktRole = MktRole.NONE,
    val preview: Boolean = false,
    val diagnostics: String = "",
    val sync: SyncUiState = SyncUiState(),
    val autoSync: Boolean = true,
    val remindersOn: Boolean = true,
    val reviewCheckOn: Boolean = true,
    val notificationsAllowed: Boolean = false,
    val gateway: GatewayConfig = GatewayConfig(),
    val gatewayTesting: Boolean = false,
    val gatewayTestMessage: String? = null,
    val lastBackupAt: Long = 0L,
    val inventory: InventorySummary = InventorySummary(),
    val working: Boolean = false,
    val restore: RestorePreview? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val roles: RoleRepository,
    private val syncManager: SyncManager,
    private val settings: SettingsStore,
    private val gatewayClient: GatewayClient,
    private val backup: BackupRepository,
    private val inventory: InventoryRepository,
    private val notifier: Notifier,
    private val background: BackgroundWork,
    private val launchInbox: LaunchInbox,
) : ViewModel() {

    private val _state = MutableStateFlow(
        SettingsUiState(email = auth.currentEmail(), role = roles.currentRole(), preview = !auth.isConfigured),
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)

    /** One-line messages for the snackbar. */
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _shareRequests = MutableSharedFlow<Uri>(extraBufferCapacity = 1)

    /** A backup file ready to be handed to the share sheet (the screen owns the Context that opens it). */
    val shareRequests: SharedFlow<Uri> = _shareRequests.asSharedFlow()

    init {
        viewModelScope.launch { roles.state.collect { _state.update { it.copy(role = roles.currentRole()) } } }
        viewModelScope.launch { roles.diagnostics.collect { text -> _state.update { it.copy(diagnostics = text) } } }
        viewModelScope.launch { syncManager.state.collect { sync -> _state.update { it.copy(sync = sync) } } }
        viewModelScope.launch { settings.autoSync.collect { on -> _state.update { it.copy(autoSync = on) } } }
        viewModelScope.launch { settings.remindersOn.collect { on -> _state.update { it.copy(remindersOn = on) } } }
        viewModelScope.launch { settings.reviewCheckOn.collect { on -> _state.update { it.copy(reviewCheckOn = on) } } }
        viewModelScope.launch { settings.gatewayConfig.collect { config -> _state.update { it.copy(gateway = config) } } }
        viewModelScope.launch { settings.lastBackupAt.collect { at -> _state.update { it.copy(lastBackupAt = at) } } }
        viewModelScope.launch { inventory.observeSummary().collect { summary -> _state.update { it.copy(inventory = summary) } } }
        viewModelScope.launch { syncManager.refreshPending() }
        refreshNotifications()
        openSharedFile()
    }

    /**
     * A JSON file was shared to the app, or opened with it: a backup shows what it holds and waits for the confirmation
     * (nothing changes before that), and the inventory app's snapshot is stored when the account may manage the inventory.
     */
    private fun openSharedFile() {
        val shared = launchInbox.takeStagedFile() ?: return
        viewModelScope.launch {
            val uri = Uri.fromFile(File(shared.path))
            _state.update { it.copy(working = true) }
            when (val decoded = backup.read(uri)) {
                is BackupDecode.Ok -> _state.update { it.copy(working = false, restore = RestorePreview(decoded.file, decoded.skipped)) }
                is BackupDecode.Bad -> {
                    _state.update { it.copy(working = false) }
                    // The screen has just opened: give it a moment to start listening for messages.
                    delay(MESSAGE_DELAY_MS)
                    if (Permissions.can(roles.currentRole(), Feature.MANAGE_INVENTORY)) {
                        importInventory(uri)
                    } else {
                        _messages.emit(decoded.message)
                    }
                }
            }
        }
    }

    /** Called when the screen comes back (after the system's permission dialog or settings page). */
    fun refreshNotifications() {
        _state.update { it.copy(notificationsAllowed = notifier.canNotify()) }
    }

    fun needsNotificationPermission(): Boolean = notifier.needsPermission()

    // ------------------------------------------------------------------ account
    fun recheckRole() {
        val uid = auth.currentUid() ?: return
        viewModelScope.launch {
            roles.load(uid, force = true)
            _messages.emit("الدور الآن: " + roles.currentRole().label)
        }
    }

    // ------------------------------------------------------------------ sync
    fun syncNow() {
        viewModelScope.launch {
            val report = syncManager.syncNow(SyncTrigger.MANUAL)
            val sync = syncManager.state.value
            _messages.emit(
                when {
                    report != null && report.ok -> "اكتملت المزامنة: " + report.summary
                    report != null -> "اكتملت مع أخطاء: " + report.firstError.orEmpty()
                    sync.offline -> "لا يوجد اتصال بالإنترنت. ستُرفع التغييرات حين يعود."
                    sync.running -> "مزامنة أخرى جارية الآن."
                    else -> sync.lastError ?: "تعذّرت المزامنة."
                },
            )
        }
    }

    fun setAutoSync(on: Boolean) {
        viewModelScope.launch { settings.setAutoSync(on) }
    }

    // ------------------------------------------------------------------ notifications
    fun setRemindersOn(on: Boolean) {
        viewModelScope.launch { settings.setRemindersOn(on) }
    }

    fun setReviewCheckOn(on: Boolean) {
        viewModelScope.launch {
            settings.setReviewCheckOn(on)
            background.setReviewCheck(on && roles.isFirebaseReady)
        }
    }

    // ------------------------------------------------------------------ gateway
    fun saveGateway(url: String, token: String) {
        viewModelScope.launch {
            settings.setGatewayConfig(GatewayConfig(url.trim(), token.trim()))
            _messages.emit("حُفظت إعدادات البوابة على هذا الجهاز")
        }
    }

    fun testGateway(url: String, token: String) {
        viewModelScope.launch {
            _state.update { it.copy(gatewayTesting = true, gatewayTestMessage = null) }
            val message = try {
                val response = gatewayClient.testConnection(GatewayConfig(url.trim(), token.trim()))
                "✓ البوابة تعمل" + (response.model?.let { " · النموذج: $it" } ?: " · الكود قديم، حدّثه لتفعيل التبديل بين النماذج")
            } catch (e: GatewayException) {
                "✗ " + e.message
            }
            _state.update { it.copy(gatewayTesting = false, gatewayTestMessage = message) }
        }
    }

    fun clearGatewayTest() {
        _state.update { it.copy(gatewayTestMessage = null) }
    }

    // ------------------------------------------------------------------ inventory snapshot
    /** The manager picked the file the inventory app exported: store it as the snapshot (the links are confirmed on their own screen). */
    fun importInventory(uri: Uri) {
        if (_state.value.working) return
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            try {
                val result = inventory.importFrom(uri)
                val skipped = if (result.skipped > 0) " (تُركت ${result.skipped})" else ""
                _messages.emit("استُوردت لقطة المخزون: ${result.items} صنفًا$skipped · مرتبط ${result.linked} · مقترح ${result.suggested} · بلا ربط ${result.unlinked}")
            } catch (e: CancellationException) {
                throw e
            } catch (e: InventoryException) {
                _messages.emit(e.message ?: "تعذّر استيراد اللقطة")
            } catch (e: Exception) {
                _messages.emit("تعذّر استيراد اللقطة")
            } finally {
                _state.update { it.copy(working = false) }
            }
        }
    }

    // ------------------------------------------------------------------ backup
    fun saveBackup() {
        if (_state.value.working) return
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            try {
                val saved = backup.saveToDownloads()
                _messages.emit("حُفظت النسخة في ${saved.path} (${saved.rows} سجل)")
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                _messages.emit(e.message ?: "تعذّر حفظ النسخة")
            } catch (e: Exception) {
                _messages.emit("تعذّر حفظ النسخة")
            } finally {
                _state.update { it.copy(working = false) }
            }
        }
    }

    fun shareBackup() {
        if (_state.value.working) return
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            try {
                _shareRequests.emit(backup.shareUri())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit("تعذّر تجهيز النسخة للمشاركة")
            } finally {
                _state.update { it.copy(working = false) }
            }
        }
    }

    /** The person picked a file: read it and show what it holds. Nothing changes until she confirms. */
    fun pickRestore(uri: Uri) {
        if (_state.value.working) return
        viewModelScope.launch {
            _state.update { it.copy(working = true) }
            when (val decoded = backup.read(uri)) {
                is BackupDecode.Ok -> _state.update { it.copy(working = false, restore = RestorePreview(decoded.file, decoded.skipped)) }
                is BackupDecode.Bad -> {
                    _state.update { it.copy(working = false) }
                    _messages.emit(decoded.message)
                }
            }
        }
    }

    fun cancelRestore() {
        _state.update { it.copy(restore = null) }
    }

    fun confirmRestore() {
        val preview = _state.value.restore ?: return
        viewModelScope.launch {
            _state.update { it.copy(working = true, restore = null) }
            try {
                val result = backup.restore(preview.file)
                _messages.emit("استُعيدت النسخة: أُضيف ${result.added} · حُدّث ${result.updated} · تُرك ${result.kept}")
                syncManager.requestSync()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit("تعذّرت الاستعادة")
            } finally {
                _state.update { it.copy(working = false) }
                syncManager.refreshPending()
            }
        }
    }

    private companion object {
        const val MESSAGE_DELAY_MS = 400L
    }
}
