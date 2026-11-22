package com.cady.cadymarketing.data.inventory

import android.content.Context
import android.net.Uri
import com.cady.cadymarketing.data.local.DocDao
import com.cady.cadymarketing.data.local.DocEntity
import com.cady.cadymarketing.data.oplog.OpLogRepository
import com.cady.cadymarketing.data.product.ProductRepository
import com.cady.cadymarketing.domain.engine.DateUtil
import com.cady.cadymarketing.domain.engine.InventoryDecode
import com.cady.cadymarketing.domain.engine.InventoryJson
import com.cady.cadymarketing.domain.engine.InventoryLinking
import com.cady.cadymarketing.domain.engine.InventoryRules
import com.cady.cadymarketing.domain.model.InventoryLinks
import com.cady.cadymarketing.domain.model.InventorySnapshot
import com.cady.cadymarketing.domain.model.OpAction
import com.cady.cadymarketing.domain.model.Product
import com.cady.cadymarketing.domain.model.StockInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** What the screens need to show stock: whether a snapshot exists, how old it is, and the stock of each linked product. */
data class StockData(val hasSnapshot: Boolean, val ageDays: Int?, val byProduct: Map<String, StockInfo>)

/** Everything the link screen and the calendar work from. */
data class InventoryData(val snapshot: InventorySnapshot?, val links: InventoryLinks, val products: List<Product>)

data class InventorySummary(
    val hasSnapshot: Boolean = false,
    val branch: String = "",
    val createdAt: Long = 0L,
    val ageDays: Int? = null,
    val items: Int = 0,
    val linked: Int = 0,
    val suggested: Int = 0,
    val unlinked: Int = 0,
)

data class ImportResult(val items: Int, val skipped: Int, val linked: Int, val suggested: Int, val unlinked: Int)

/** A sentence to show the manager when an import or a link cannot be made. */
class InventoryException(message: String) : Exception(message)

/**
 * The inventory snapshot and its links, kept as two stored documents (`inventory` and `inventory_links`). They travel
 * with the rest of the brand documents through the sync, so a marketer sees the same stock badges. The manager imports the
 * snapshot from a file the inventory app exports and confirms which line is which Kady product; nothing is ever linked
 * by similarity alone, and nothing here writes to the inventory app or to Kady.
 */
@Singleton
class InventoryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val docs: DocDao,
    private val products: ProductRepository,
    private val opLog: OpLogRepository,
) {
    // The stored text is compared first: any edit to another document (the brand, say) wakes these flows, and a large
    // snapshot should not be parsed again for nothing.
    fun observeSnapshot(): Flow<InventorySnapshot?> =
        docs.observe(SNAPSHOT_KEY)
            .map { doc -> if (doc == null || doc.deletedAt != null) null else doc.json }
            .distinctUntilChanged()
            .map { json -> InventoryJson.decodeStored(json) }

    fun observeLinks(): Flow<InventoryLinks> =
        docs.observe(LINKS_KEY)
            .map { doc -> if (doc == null || doc.deletedAt != null) null else doc.json }
            .distinctUntilChanged()
            .map { json -> InventoryJson.decodeLinks(json) }

    fun observeData(): Flow<InventoryData> =
        combine(observeSnapshot(), observeLinks(), products.observeProducts()) { snapshot, links, list -> InventoryData(snapshot, links, list) }

    /** The stock of each product that has a confirmed link. Without a snapshot there is nothing to show. */
    fun observeStock(): Flow<StockData> = combine(observeSnapshot(), observeLinks()) { snapshot, links ->
        if (snapshot == null) {
            StockData(false, null, emptyMap())
        } else {
            StockData(
                hasSnapshot = true,
                ageDays = InventoryRules.ageDays(snapshot.createdAt, System.currentTimeMillis()),
                byProduct = InventoryLinking.byProduct(snapshot, links, DateUtil.today()),
            )
        }
    }

    fun observeSummary(): Flow<InventorySummary> = observeData().map { summary(it) }

    private fun summary(data: InventoryData): InventorySummary {
        val snapshot = data.snapshot ?: return InventorySummary()
        val states = InventoryLinking.states(snapshot, data.links, data.products)
        val linked = states.count { it.productId != null }
        val suggested = states.count { it.productId == null && it.suggestedProductId != null }
        return InventorySummary(
            hasSnapshot = true,
            branch = snapshot.branch,
            createdAt = snapshot.createdAt,
            ageDays = InventoryRules.ageDays(snapshot.createdAt, System.currentTimeMillis()),
            items = snapshot.items.size,
            linked = linked,
            suggested = suggested,
            unlinked = states.size - linked - suggested,
        )
    }

    /** Reads the file the person picked and stores it as the snapshot. Throws [InventoryException] with a sentence to show. */
    suspend fun importFrom(uri: Uri): ImportResult {
        val text = readText(uri) ?: throw InventoryException("تعذّرت قراءة الملف.")
        val ok = when (val decoded = InventoryJson.decode(text, System.currentTimeMillis())) {
            is InventoryDecode.Ok -> decoded
            is InventoryDecode.Bad -> throw InventoryException(decoded.message)
        }
        val json = InventoryJson.encode(ok.snapshot)
        if (json.toByteArray(Charsets.UTF_8).size > InventoryRules.MAX_DOC_BYTES) {
            throw InventoryException("اللقطة كبيرة جدًا (${ok.snapshot.items.size} صنفًا) لتُخزَّن وتُزامَن. صدّر أصناف الفرع الذي تروّجه فقط.")
        }
        docs.upsert(DocEntity(SNAPSHOT_KEY, json, System.currentTimeMillis(), null))
        val result = summary(InventoryData(ok.snapshot, observeLinks().first(), products.observeProducts().first()))
        opLog.record(
            OpAction.INVENTORY_IMPORTED,
            ok.snapshot.branch.ifBlank { "لقطة المخزون" },
            "${ok.snapshot.items.size} صنف · مرتبط ${result.linked}",
        )
        return ImportResult(ok.snapshot.items.size, ok.skipped, result.linked, result.suggested, result.unlinked)
    }

    /** The manager confirms that line [key] is Kady product [productId]. A product belongs to one line: it moves from another. */
    suspend fun link(key: String, productId: String, itemName: String) {
        saveLinks(InventoryLinking.withLink(currentLinks(), key, productId))
        opLog.record(OpAction.INVENTORY_LINKED, itemName)
    }

    /** Confirms every suggestion at once (each was an exact name match, unambiguous on both sides). */
    suspend fun linkAll(suggestions: Map<String, String>) {
        if (suggestions.isEmpty()) return
        saveLinks(InventoryLinking.withLinks(currentLinks(), suggestions))
        opLog.record(OpAction.INVENTORY_LINKED, "${suggestions.size} صنف", "تأكيد المقترحات")
    }

    suspend fun unlink(key: String, itemName: String) {
        saveLinks(InventoryLinking.withoutLink(currentLinks(), key))
        opLog.record(OpAction.INVENTORY_UNLINKED, itemName)
    }

    private suspend fun currentLinks(): InventoryLinks = InventoryJson.decodeLinks(docs.get(LINKS_KEY)?.takeIf { it.deletedAt == null }?.json)

    private suspend fun saveLinks(links: InventoryLinks) {
        docs.upsert(DocEntity(LINKS_KEY, InventoryJson.encodeLinks(links), System.currentTimeMillis(), null))
    }

    private suspend fun readText(uri: Uri): String? = withContext(Dispatchers.IO) {
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER)
                var total = 0
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    total += read
                    if (total > MAX_FILE_BYTES) return@withContext null
                    out.write(buffer, 0, read)
                }
                out.toByteArray().toString(Charsets.UTF_8)
            }
        } catch (e: IOException) {
            null
        } catch (e: SecurityException) {
            null
        }
    }

    private companion object {
        const val SNAPSHOT_KEY = "inventory"
        const val LINKS_KEY = "inventory_links"
        const val BUFFER = 16 * 1024
        const val MAX_FILE_BYTES = 8 * 1024 * 1024
    }
}
