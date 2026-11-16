package com.cady.cadymarketing.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.catalog.CatalogRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

/**
 * Work the signed-in app does in the background each time it opens. Phase 2: keep Kady's catalogue fresh
 * (quietly, at most once every ten minutes); later phases add the sync and the reminders here.
 */
@HiltViewModel
class ShellViewModel @Inject constructor(
    private val catalog: CatalogRepository,
) : ViewModel() {

    fun onShellOpened() {
        viewModelScope.launch { catalog.refreshIfStale(CATALOG_MAX_AGE_MS) }
    }

    private companion object {
        const val CATALOG_MAX_AGE_MS = 10L * 60L * 1000L
    }
}
