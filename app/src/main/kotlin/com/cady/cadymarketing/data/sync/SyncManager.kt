package com.cady.cadymarketing.data.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.cady.cadymarketing.data.auth.AuthRepository
import com.cady.cadymarketing.data.catalog.CatalogRepository
import com.cady.cadymarketing.data.cloud.CloudErrors
import com.cady.cadymarketing.data.local.SyncDao
import com.cady.cadymarketing.data.oplog.OpLogRepository
import com.cady.cadymarketing.data.settings.SettingsStore
import com.cady.cadymarketing.data.team.RoleRepository
import com.cady.cadymarketing.domain.engine.SyncEngine
import com.cady.cadymarketing.domain.engine.SyncReport
import com.cady.cadymarketing.domain.engine.SyncSpecs
import com.cady.cadymarketing.domain.model.MktRole
import com.cady.cadymarketing.domain.model.OpAction
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex

/** What started a run. A person's own tap is the only one that writes a line in the operations log. */
enum class SyncTrigger { AUTO, MANUAL, BACKGROUND }

/** What the sync card shows. */
data class SyncUiState(
    val running: Boolean = false,
    val lastAt: Long? = null,
    val lastSummary: String? = null,
    val lastError: String? = null,
    /** Rows the cloud refused and that were replaced by its copy (a marketer's edit of a post the manager had already approved). */
    val reverted: Int = 0,
    /** Rows changed on this device and not uploaded yet. */
    val pending: Int = 0,
    val offline: Boolean = false,
)

/**
 * Runs the sync (see [SyncEngine]) one run at a time, quietly after a change and whenever the person asks.
 * Nothing is sent without a connection, and nothing runs for an account that has no role in the app.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: AuthRepository,
    private val roles: RoleRepository,
    local: RoomSyncLocal,
    remote: FirestoreSyncRemote,
    private val syncDao: SyncDao,
    private val settings: SettingsStore,
    private val opLog: OpLogRepository,
    private val catalog: CatalogRepository,
) {
    private val engine = SyncEngine(local, remote, SyncSpecs.all)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncUiState())
    val state: StateFlow<SyncUiState> = _state.asStateFlow()
    private val triggers = Channel<Unit>(Channel.CONFLATED)
    private var watcher: Job? = null

    /**
     * Starts watching the database: a few seconds after the last change, and once when the app opens, a run is made
     * (when the person left automatic sync on). Safe to call again; it does nothing while the watcher is alive.
     */
    fun start(scope: CoroutineScope) {
        if (watcher?.isActive == true) return
        watcher = scope.launch {
            launch { syncDao.observeChangeMarker().distinctUntilChanged().collect { triggers.trySend(Unit) } }
            for (ignored in triggers) {
                delay(DEBOUNCE_MS)
                // Fold every change that came in while waiting into this one run.
                while (triggers.tryReceive().isSuccess) { /* nothing to do: the run below covers them */ }
                if (settings.autoSync.first()) syncNow(SyncTrigger.AUTO)
            }
        }
    }

    /** Asks for a run soon (after the short wait that folds changes together), as when the app opens. */
    fun requestSync() {
        triggers.trySend(Unit)
    }

    /** One run. Returns its report, or null when nothing ran (nobody signed in, no role, no connection, or a run is already going). */
    suspend fun syncNow(trigger: SyncTrigger = SyncTrigger.MANUAL): SyncReport? {
        val uid = auth.currentUid() ?: return null
        if (!mutex.tryLock()) return null
        try {
            val role = roles.resolveNow(uid)
            if (role == MktRole.NONE) return null
            if (!isOnline()) {
                _state.update { it.copy(running = false, offline = true, pending = pendingOrKeep(role, it.pending)) }
                return null
            }
            _state.update { it.copy(running = true, offline = false, lastError = null) }
            val report = engine.run(role, System.currentTimeMillis()) { CloudErrors.message(it) }
            afterRun(role, report, trigger)
            return report
        } catch (e: CancellationException) {
            _state.update { it.copy(running = false) }
            throw e
        } catch (e: Exception) {
            _state.update { it.copy(running = false, lastError = CloudErrors.message(e)) }
            return null
        } finally {
            mutex.unlock()
        }
    }

    /** Counts what waits to be uploaded (for the sync card). */
    suspend fun refreshPending() {
        val role = roles.currentRole()
        _state.update { it.copy(pending = pendingOrKeep(role, it.pending)) }
    }

    private suspend fun afterRun(role: MktRole, report: SyncReport, trigger: SyncTrigger) {
        if (role == MktRole.MARKETER) {
            try {
                catalog.restoreFromMirror()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // The catalogue stays as it was; the next run tries again.
            }
        }
        val pending = pendingOrKeep(role, 0)
        _state.value = SyncUiState(
            running = false,
            lastAt = report.at,
            lastSummary = report.summary,
            lastError = report.firstError,
            reverted = report.reverted,
            pending = pending,
            offline = false,
        )
        val somethingMoved = report.pushed + report.pulled + report.numbered + report.reverted > 0
        if (trigger == SyncTrigger.MANUAL && (somethingMoved || !report.ok)) {
            opLog.record(OpAction.SYNC_RUN, report.summary, report.firstError.orEmpty())
        }
    }

    private suspend fun pendingOrKeep(role: MktRole, keep: Int): Int =
        try {
            engine.pendingCount(role)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            keep
        }

    private fun isOnline(): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private companion object {
        const val DEBOUNCE_MS = 4_000L
    }
}
