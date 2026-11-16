package com.cady.cadymarketing.data.catalog

import androidx.room.withTransaction
import com.cady.cadymarketing.data.auth.AuthRepository
import com.cady.cadymarketing.data.cloud.CatalogSource
import com.cady.cadymarketing.data.cloud.RawDoc
import com.cady.cadymarketing.data.local.CadyMarketingDatabase
import com.cady.cadymarketing.data.local.CatalogDao
import com.cady.cadymarketing.data.local.CatalogOfferEntity
import com.cady.cadymarketing.data.local.CatalogProductEntity
import com.cady.cadymarketing.data.local.ProductExtraEntity
import com.cady.cadymarketing.data.local.SyncMetaDao
import com.cady.cadymarketing.data.local.SyncMetaEntity
import com.cady.cadymarketing.data.product.SampleCatalog
import com.cady.cadymarketing.domain.engine.DateUtil
import com.cady.cadymarketing.domain.engine.KadyMapper
import com.cady.cadymarketing.domain.engine.KadyOffer
import com.cady.cadymarketing.domain.engine.KadyProduct
import com.cady.cadymarketing.domain.engine.Mapped
import com.cady.cadymarketing.domain.engine.OfferResolver
import com.cady.cadymarketing.domain.engine.ProductText
import com.cady.cadymarketing.domain.model.Product
import com.cady.cadymarketing.domain.model.ProductRow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

/** What the last refresh from Kady read. Shown in the diagnostics dialog so a field-name mismatch is easy to see. */
data class RefreshReport(
    val products: Int,
    val offers: Int,
    val skipped: List<String>,
    val sampleProduct: String,
    val sampleOffer: String,
    val at: Long,
)

/** A refresh that read nothing usable. The message says why. The catalogue already on the device is left untouched. */
class CatalogException(message: String) : Exception(message)

/**
 * The catalogue of the marketing app: Kady's products and offers copied to this device (read only), and what the
 * marketing app adds on top (a picture and features per product). Everything works offline from the copy.
 * Without Firebase (preview mode) the sample catalogue stands in for Kady.
 */
@Singleton
class CatalogRepository @Inject constructor(
    private val database: CadyMarketingDatabase,
    private val dao: CatalogDao,
    private val syncMeta: SyncMetaDao,
    private val source: CatalogSource,
    private val auth: AuthRepository,
) {
    val isPreview: Boolean get() = !auth.isConfigured

    fun observeRows(): Flow<List<ProductRow>> =
        combine(dao.observeProducts(), dao.observeOffers(), dao.observeExtras()) { products, offers, extras ->
            val extraById = extras.associateBy { it.productId }
            if (isPreview) {
                SampleCatalog.products.map { sample -> ProductRow(withExtra(sample, extraById[sample.id]), true) }
            } else {
                val today = DateUtil.today()
                val models = offers.map { it.toModel() }
                products.map { row ->
                    val extra = extraById[row.id]
                    val kady = KadyProduct(row.id, row.name, row.unit, row.price, row.active)
                    ProductRow(
                        product = OfferResolver.toProduct(
                            kady, models, today,
                            features = extra?.let { ProductText.features(it.features) }.orEmpty(),
                            picturePath = extra?.picturePath,
                        ),
                        active = row.active,
                    )
                }
            }
        }

    /** The products the Studio may write about: the ones Kady still sells. */
    fun observeProducts(): Flow<List<Product>> =
        observeRows().map { rows -> rows.filter { it.active }.map { it.product } }

    fun observeLastSync(): Flow<Long?> = syncMeta.observe(SYNC_KEY).map { it?.lastSyncAt }

    /** Reads Kady and replaces the copy on this device. Throws the cloud's exception, or [CatalogException]. */
    suspend fun refresh(): RefreshReport {
        val raw = source.fetch()
        val skipped = ArrayList<String>()
        val products = ArrayList<KadyProduct>()
        for (doc in raw.products) {
            when (val mapped = KadyMapper.product(doc.id, doc.data)) {
                is Mapped.Ok -> products.add(mapped.value)
                is Mapped.Skipped -> skipped.add(mapped.reason)
            }
        }
        val offers = ArrayList<KadyOffer>()
        for (doc in raw.offers) {
            when (val mapped = KadyMapper.offer(doc.id, doc.data)) {
                is Mapped.Ok -> offers.add(mapped.value)
                is Mapped.Skipped -> skipped.add(mapped.reason)
            }
        }
        if (raw.products.isEmpty()) {
            throw CatalogException("لم يرجع كادي أي صنف. تحقق من وجود أصناف في كادي ومن صلاحية حسابك.")
        }
        if (products.isEmpty()) {
            throw CatalogException("قرأت ${raw.products.size} مستندًا من كادي لكن لم أفهم أيًّا منها: ${skipped.firstOrNull().orEmpty()}")
        }
        val now = System.currentTimeMillis()
        database.withTransaction {
            dao.clearProducts()
            dao.clearOffers()
            dao.upsertProducts(products.map { CatalogProductEntity(it.id, it.name, it.unit, it.price, it.active, now) })
            dao.upsertOffers(offers.map { it.toEntity() })
        }
        syncMeta.upsert(SyncMetaEntity(SYNC_KEY, now))
        return RefreshReport(
            products = products.size,
            offers = offers.size,
            skipped = skipped,
            sampleProduct = raw.products.firstOrNull()?.let { describe(it) }.orEmpty(),
            sampleOffer = raw.offers.firstOrNull()?.let { describe(it) } ?: "لا توجد عروض في كادي",
            at = now,
        )
    }

    /** Refreshes quietly when the copy is older than [maxAgeMs]. Returns true only when a refresh happened and worked. */
    suspend fun refreshIfStale(maxAgeMs: Long): Boolean {
        if (isPreview) return false
        val last = syncMeta.get(SYNC_KEY)?.lastSyncAt
        if (last != null && System.currentTimeMillis() - last < maxAgeMs) return false
        return try {
            refresh()
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    /** Links (or, with null, unlinks) a picture to a product. Returns the picture that was linked before. */
    suspend fun linkPicture(productId: String, fileName: String?): String? {
        val current = dao.extra(productId)
        dao.upsertExtra(ProductExtraEntity(productId, fileName, current?.features.orEmpty(), System.currentTimeMillis()))
        return current?.picturePath
    }

    suspend fun setFeatures(productId: String, features: List<String>) {
        val current = dao.extra(productId)
        dao.upsertExtra(
            ProductExtraEntity(productId, current?.picturePath, ProductText.featuresText(features), System.currentTimeMillis()),
        )
    }

    private fun withExtra(base: Product, extra: ProductExtraEntity?): Product {
        if (extra == null) return base
        val features = ProductText.features(extra.features)
        return base.copy(features = features.ifEmpty { base.features }, picturePath = extra.picturePath)
    }

    private fun describe(doc: RawDoc): String =
        (doc.id + " ← " + doc.data.entries.joinToString(" · ") { (key, value) -> "$key=${shorten(value)}" }).take(MAX_SAMPLE)

    private fun shorten(value: Any?): String = (value?.toString() ?: "null").take(40)

    private fun CatalogOfferEntity.toModel() =
        KadyOffer(id, productId, percent, offerPrice, amount, startDate, endDate, active)

    private fun KadyOffer.toEntity() =
        CatalogOfferEntity(id, productId, percent, offerPrice, amount, startDate, endDate, active)

    companion object {
        const val SYNC_KEY = "catalog:pull"
        private const val MAX_SAMPLE = 600
    }
}
