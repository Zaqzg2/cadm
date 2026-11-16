package com.cady.cadymarketing.data.product

import com.cady.cadymarketing.data.catalog.CatalogRepository
import com.cady.cadymarketing.domain.model.Product
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow

/** Where the Studio gets its products. */
interface ProductRepository {
    fun observeProducts(): Flow<List<Product>>
}

/** What stands in for Kady in preview mode: the original tool's seed plus two labelled test items, so every rule can be tried. */
object SampleCatalog {
    val products: List<Product> = listOf(
        Product(
            id = "i1",
            name = "سائل جلي كادي — ليمون",
            unit = "كرتونة (4 جراكن × 5 لتر)",
            price = 7000L,
            offerPrice = 6800L,
        ),
        Product(
            id = "demo-plain",
            name = "منتج تجريبي — بلا عرض",
            unit = "جركن (5 لتر)",
            price = 1800L,
            features = listOf("عبوة عملية سهلة الحمل", "مناسب للاستخدام المنزلي"),
        ),
        Product(
            id = "demo-expired",
            name = "منتج تجريبي — عرض منتهٍ",
            unit = "علبة",
            price = 900L,
            offerPrice = 800L,
            offerEnd = "2026-01-01",
        ),
    )
}

/** The Studio's products: Kady's catalogue (or the sample one in preview mode), with offers, pictures and features. */
@Singleton
class CatalogProductRepository @Inject constructor(
    private val catalog: CatalogRepository,
) : ProductRepository {
    override fun observeProducts(): Flow<List<Product>> = catalog.observeProducts()
}
