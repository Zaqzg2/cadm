package com.cady.cadymarketing.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.audience.AudienceRepository
import com.cady.cadymarketing.data.catalog.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Work the signed-in app does in the background each time it opens. Phase 2: keep Kady's catalogue fresh
 * (quietly, at most once every ten minutes). Phase 4: keep the copy of Kady's customers fresh (at most every six
 * hours: they change slowly); later phases add the sync and the reminders here.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val audience: AudienceRepository,
) : ViewModel() {

    fun onShellOpened() {
        viewModelScope.launch { catalog.refreshIfStale(CATALOG_MAX_AGE_MS) }
        viewModelScope.launch { audience.refreshIfStale(CUSTOMERS_MAX_AGE_MS) }
    }

    private companion object {
        const val CATALOG_MAX_AGE_MS = 10L * 60L * 1000L
        const val CUSTOMERS_MAX_AGE_MS = 6L * 60L * 60L * 1000L
    }
}
