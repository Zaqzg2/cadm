package com.cady.cadymarketing.feature.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.inventory.InventoryRepository
import com.cady.cadymarketing.domain.engine.InventoryLinking
import com.cady.cadymarketing.domain.engine.ItemLinkState
import com.cady.cadymarketing.domain.model.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class LinkFilter(val label: String) {
    ALL("الكل"),
    UNLINKED("بلا ربط"),
    SUGGESTED("مقترح"),
    LINKED("مرتبط"),
}

/** One line of the inventory, with the names of the product it is linked to and the one suggested for it. */
data class LinkRow(val state: ItemLinkState, val productName: String?, val suggestedName: String?) {
    val linked: Boolean get() = state.productId != null
    val suggested: Boolean get() = state.productId == null && state.suggestedProductId != null
}

data class InventoryLinksState(
    val loaded: Boolean = false,
    val hasSnapshot: Boolean = false,
    val rows: List<LinkRow> = emptyList(),
    val products: List<Product> = emptyList(),
    val filter: LinkFilter = LinkFilter.ALL,
    val query: String = "",
) {
    val linkedCount: Int get() = rows.count { it.linked }
    val suggestedCount: Int get() = rows.count { it.suggested }
    val unlinkedCount: Int get() = rows.size - linkedCount - suggestedCount

    val visible: List<LinkRow>
        get() {
            val needle = query.trim()
            return rows.filter { row ->
                val byFilter = when (filter) {
                    LinkFilter.ALL -> true
                    LinkFilter.UNLINKED -> !row.linked && !row.suggested
                    LinkFilter.SUGGESTED -> row.suggested
                    LinkFilter.LINKED -> row.linked
                }
                byFilter && (needle.isEmpty() || row.state.item.name.contains(needle, ignoreCase = true) || row.state.item.barcode.contains(needle) || row.state.item.itemNo.contains(needle, ignoreCase = true))
            }
        }
}

/** The manager's screen for confirming which inventory line is which Kady product. */
@HiltViewModel
class InventoryLinksViewModel @Inject constructor(
    private val inventory: InventoryRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(InventoryLinksState())
    val state: StateFlow<InventoryLinksState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            inventory.observeData().collect { data ->
                val snapshot = data.snapshot
                if (snapshot == null) {
                    _state.update { it.copy(loaded = true, hasSnapshot = false, rows = emptyList(), products = data.products) }
                } else {
                    val names = data.products.associate { it.id to it.name }
                    val rows = InventoryLinking.states(snapshot, data.links, data.products).map { s ->
                        LinkRow(s, s.productId?.let { names[it] }, s.suggestedProductId?.let { names[it] })
                    }
                    _state.update { it.copy(loaded = true, hasSnapshot = true, rows = rows, products = data.products.sortedBy { p -> p.name }) }
                }
            }
        }
    }

    fun setFilter(filter: LinkFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    /** Confirms the suggested product of one line. */
    fun confirm(row: LinkRow) {
        val productId = row.state.suggestedProductId ?: return
        launchSafely { inventory.link(row.state.item.key, productId, row.state.item.name) }
    }

    fun choose(row: LinkRow, product: Product) {
        launchSafely {
            inventory.link(row.state.item.key, product.id, row.state.item.name)
            _messages.emit("رُبط «${row.state.item.name}» بـ «${product.name}»")
        }
    }

    fun unlink(row: LinkRow) {
        launchSafely { inventory.unlink(row.state.item.key, row.state.item.name) }
    }

    /** Confirms every suggestion (each is an exact name match that no other line or product could also claim). */
    fun confirmAll() {
        val suggestions = _state.value.rows.filter { it.suggested }.associate { it.state.item.key to it.state.suggestedProductId!! }
        launchSafely {
            inventory.linkAll(suggestions)
            _messages.emit("أُكّد ربط ${suggestions.size} صنف")
        }
    }

    private fun launchSafely(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.emit("تعذّر حفظ الربط")
            }
        }
    }
}
