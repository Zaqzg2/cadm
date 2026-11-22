package com.cady.cadymarketing.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.audience.AudienceRepository
import com.cady.cadymarketing.data.catalog.CatalogRepository
import com.cady.cadymarketing.data.notify.BackgroundWork
import com.cady.cadymarketing.data.notify.ReminderCoordinator
import com.cady.cadymarketing.data.settings.SettingsStore
import com.cady.cadymarketing.data.sync.SyncManager
import com.cady.cadymarketing.data.team.RoleRepository
import com.cady.cadymarketing.domain.model.MktRole
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Work the signed-in app does in the background each time it opens. Phase 2: keep Kady's catalogue fresh
 * (quietly, at most once every ten minutes). Phase 4: keep the copy of Kady's customers fresh (at most every six
 * hours: they change slowly). Phase 5: the sync (soon after a change and once now), the reminders of approved posts,
 * and the periodic check. The manager reads Kady's catalogue and customers; a marketer gets the catalogue from the copy
 * the manager publishes (her account may not read Kady's collections), and never touches the customers.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val audience: AudienceRepository,
    private val roles: RoleRepository,
    private val sync: SyncManager,
    private val reminders: ReminderCoordinator,
    private val background: BackgroundWork,
    private val settings: SettingsStore,
) : ViewModel() {

    fun onShellOpened() {
        when (roles.currentRole()) {
            MktRole.MANAGER -> {
                viewModelScope.launch { catalog.refreshIfStale(CATALOG_MAX_AGE_MS) }
                viewModelScope.launch { audience.refreshIfStale(CUSTOMERS_MAX_AGE_MS) }
            }
            MktRole.MARKETER -> viewModelScope.launch { catalog.restoreFromMirror() }
            MktRole.NONE -> return
        }
        sync.start(viewModelScope)
        sync.requestSync()
        reminders.start(viewModelScope)
        viewModelScope.launch {
            // The periodic check needs an account to sync with: it is not scheduled in preview mode.
            background.setReviewCheck(roles.isFirebaseReady && settings.reviewCheckOn.first())
        }
    }

    private companion object {
        const val CATALOG_MAX_AGE_MS = 10L * 60L * 1000L
        const val CUSTOMERS_MAX_AGE_MS = 6L * 60L * 60L * 1000L
    }
}
