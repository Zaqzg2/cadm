package com.cady.cadymarketing.feature.products

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.catalog.CatalogException
import com.cady.cadymarketing.data.catalog.CatalogRepository
import com.cady.cadymarketing.data.catalog.RefreshReport
import com.cady.cadymarketing.data.cloud.CloudErrors
import com.cady.cadymarketing.data.inventory.InventoryRepository
import com.cady.cadymarketing.data.media.PictureStore
import com.cady.cadymarketing.domain.engine.InventoryRules
import com.cady.cadymarketing.domain.engine.ProductText
import com.cady.cadymarketing.domain.model.ProductRow
import com.cady.cadymarketing.domain.model.StockInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** What the stock says about a product, as a filter of the list (it only shows once an inventory snapshot exists). */
enum class StockFilter(val label: String) {
    ALL("الكل"),
    PROMOTE("يحتاج ترويجًا"),
    AVOID("لا تروّج"),
}

data class ProductsState(
    val rows: List<ProductRow> = emptyList(),
    val query: String = "",
    val refreshing: Boolean = false,
    val lastSyncAt: Long? = null,
    val report: RefreshReport? = null,
    val isPreview: Boolean = false,
    /** The stock of each product that has a confirmed link to a line of the inventory snapshot. */
    val stock: Map<String, StockInfo> = emptyMap(),
    val hasSnapshot: Boolean = false,
    val stockFilter: StockFilter = StockFilter.ALL,
) {
    /** The rows that match the search box (name only) and the stock filter. */
    val visible: List<ProductRow>
        get() {
            val needle = query.trim()
            return rows.filter { row ->
                val byName = needle.isEmpty() || row.product.name.contains(needle, ignoreCase = true)
                val info = stock[row.product.id]
                val byStock = when (stockFilter) {
                    StockFilter.ALL -> true
                    StockFilter.PROMOTE -> info != null && InventoryRules.needsPromotion(info)
                    StockFilter.AVOID -> info != null && InventoryRules.shouldAvoid(info)
                }
                byName && byStock
            }
        }
}

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val catalog: CatalogRepository,
    private val pictures: PictureStore,
    private val inventory: InventoryRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ProductsState(isPreview = catalog.isPreview))
    val state: StateFlow<ProductsState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            catalog.observeRows().collect { rows -> _state.update { it.copy(rows = rows) } }
        }
        viewModelScope.launch {
            catalog.observeLastSync().collect { at -> _state.update { it.copy(lastSyncAt = at) } }
        }
        viewModelScope.launch {
            inventory.observeStock().collect { data ->
                _state.update {
                    it.copy(
                        stock = data.byProduct,
                        hasSnapshot = data.hasSnapshot,
                        stockFilter = if (data.hasSnapshot) it.stockFilter else StockFilter.ALL,
                    )
                }
            }
        }
    }

    fun setStockFilter(filter: StockFilter) {
        _state.update { it.copy(stockFilter = filter) }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    /** Reads Kady again. The answer says what was read, and what could not be understood. */
    fun refresh() {
        if (_state.value.refreshing) return
        if (catalog.isPreview) {
            say("وضع المعاينة: الأصناف تجريبية ولا تُقرأ من كادي")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            try {
                val report = catalog.refresh()
                _state.update { it.copy(report = report) }
                val skipped = if (report.skipped.isEmpty()) "" else " (تخطّيت ${report.skipped.size})"
                say("قُرئ ${report.products} صنفًا و${report.offers} عرضًا من كادي$skipped")
            } catch (e: CancellationException) {
                throw e
            } catch (e: CatalogException) {
                say(e.message ?: "تعذّرت قراءة الأصناف")
            } catch (e: Exception) {
                say(CloudErrors.message(e))
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun linkPicture(productId: String, uri: Uri) {
        viewModelScope.launch {
            val file = pictures.import(uri)
            if (file == null) {
                say("تعذّر قراءة الصورة، جرّب صورة أخرى")
                return@launch
            }
            val previous = catalog.linkPicture(productId, file)
            if (previous != null) pictures.delete(previous)
            say("رُبطت الصورة بالصنف")
        }
    }

    fun removePicture(productId: String) {
        viewModelScope.launch {
            val previous = catalog.linkPicture(productId, null)
            if (previous != null) pictures.delete(previous)
            say("فُصلت الصورة عن الصنف")
        }
    }

    fun saveFeatures(productId: String, text: String) {
        viewModelScope.launch {
            catalog.setFeatures(productId, ProductText.features(text))
            say("حُفظت المزايا")
        }
    }

    suspend fun loadThumb(fileName: String): Bitmap? = withContext(Dispatchers.IO) { pictures.load(fileName, THUMB_SIDE) }

    private fun say(text: String) {
        _messages.tryEmit(text)
    }

    private companion object {
        const val THUMB_SIDE = 240
    }
}
