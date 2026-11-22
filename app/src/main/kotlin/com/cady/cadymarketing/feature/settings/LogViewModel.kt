package com.cady.cadymarketing.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.oplog.OpLogRepository
import com.cady.cadymarketing.domain.model.OpLogEntry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class LogViewModel @Inject constructor(
    opLog: OpLogRepository,
) : ViewModel() {
    /** The latest lines, newest first. */
    val entries: StateFlow<List<OpLogEntry>> = opLog.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
