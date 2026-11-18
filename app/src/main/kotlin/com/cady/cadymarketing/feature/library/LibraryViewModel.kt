package com.cady.cadymarketing.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.content.ContentRepository
import com.cady.cadymarketing.domain.engine.LibraryRules
import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.SavedContent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Every saved post, the search typed so far and the status picked in the chips. */
data class LibraryState(
    val loaded: Boolean = false,
    val all: List<SavedContent> = emptyList(),
    val query: String = "",
    val filter: ContentStatus? = null,
) {
    val visible: List<SavedContent> get() = LibraryRules.filter(all, filter, query)
    val counts: Map<ContentStatus, Int> get() = LibraryRules.counts(all)
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val content: ContentRepository,
) : ViewModel() {

    private val query = MutableStateFlow("")
    private val filter = MutableStateFlow<ContentStatus?>(null)

    val state: StateFlow<LibraryState> = combine(content.observeAll(), query, filter) { all, text, status ->
        LibraryState(loaded = true, all = all, query = text, filter = status)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun setQuery(text: String) {
        query.value = text
    }

    fun setFilter(status: ContentStatus?) {
        filter.value = status
    }

    fun move(id: String, target: ContentStatus) {
        viewModelScope.launch {
            content.setStatus(id, target)
            _messages.tryEmit("صار المنشور «${target.label}»")
        }
    }

    fun duplicate(id: String) {
        viewModelScope.launch {
            val copy = content.duplicate(id)
            _messages.tryEmit(if (copy == null) "المنشور لم يعد موجودًا" else "نُسخ المنشور كمسودة جديدة")
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            content.delete(id)
            _messages.tryEmit("حُذف المنشور")
        }
    }
}
