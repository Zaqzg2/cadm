package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.InventoryLinks
import com.cady.cadymarketing.domain.model.InventorySnapshot
import com.cady.cadymarketing.domain.model.Product
import com.cady.cadymarketing.domain.model.SavedContent
import com.cady.cadymarketing.domain.model.StockInfo
import com.cady.cadymarketing.domain.model.StockStatus

/** A delivery due on a day, for the calendar. [productId] is set when the line is linked to a Kady product. */
data class Arrival(val itemName: String, val productId: String?, val qty: Long, val unit: String)

/** One product of a campaign with what the stock says about it. */
data class CampaignStockRow(val productId: String, val productName: String, val info: StockInfo?)

/** What the stock adds to the home alerts, the calendar and the campaign page. Pure Kotlin. */
object InventoryInsights {
    private const val MS_PER_DAY = 86_400_000L
    private const val RECENT_PUBLISHED_DAYS = 7
    private const val RECENT_POST_DAYS = 30
    private const val MAX_NAMED = 3

    /**
     * Alerts for the home screen: a product that ran out while a post for it is ready or just published (urgent), products in
     * surplus with no post lately, and a snapshot that is getting old. [stock] is by product id; [ageDays] is the snapshot's age.
     */
    fun alerts(
        stock: Map<String, StockInfo>,
        products: List<Product>,
        posts: List<SavedContent>,
        nowMs: Long,
        ageDays: Int?,
    ): List<HomeAlert> {
        if (stock.isEmpty() && ageDays == null) return emptyList()
        val alerts = ArrayList<HomeAlert>()

        val promoted = posts.filter { post ->
            post.status == ContentStatus.PENDING ||
                post.status == ContentStatus.APPROVED ||
                (post.status == ContentStatus.PUBLISHED && post.updatedAt >= nowMs - RECENT_PUBLISHED_DAYS * MS_PER_DAY)
        }.map { it.productId }.toSet()
        val outPromoted = products.filter { stock[it.id]?.status == StockStatus.OUT && it.id in promoted }
        for (product in outPromoted.take(MAX_NAMED)) {
            val arrival = stock[product.id]?.nextArrival?.let { " (تصل بضاعة في ${it.date})" }.orEmpty()
            alerts.add(
                HomeAlert(
                    AlertLevel.URGENT,
                    "نفد «${product.name}» وله منشور جاهز أو منشور حديثًا",
                    "راجع منشوراته قبل نشرها$arrival",
                    AlertTarget.LIBRARY,
                ),
            )
        }

        val recentlyPosted = posts
            .filter { it.status != ContentStatus.ARCHIVED && it.updatedAt >= nowMs - RECENT_POST_DAYS * MS_PER_DAY }
            .map { it.productId }.toSet()
        val surplus = products.filter { stock[it.id]?.status == StockStatus.OVER && it.id !in recentlyPosted }
        if (surplus.size == 1) {
            alerts.add(
                HomeAlert(
                    AlertLevel.NOTICE,
                    "«${surplus[0].name}» فائض في المخزون ولا منشور له منذ شهر",
                    "اكتب منشورًا يروّجه",
                    AlertTarget.STUDIO,
                    productId = surplus[0].id,
                ),
            )
        } else if (surplus.size > 1) {
            alerts.add(
                HomeAlert(
                    AlertLevel.NOTICE,
                    "${surplus.size} أصناف فائضة في المخزون ولا منشور لها منذ شهر",
                    surplus.take(MAX_NAMED).joinToString("، ") { it.name },
                    AlertTarget.PRODUCTS,
                ),
            )
        }

        if (InventoryRules.isStale(ageDays)) {
            alerts.add(
                HomeAlert(
                    AlertLevel.INFO,
                    "لقطة المخزون قديمة ($ageDays يومًا)",
                    "استورد لقطة جديدة من الفريق والإعدادات",
                    AlertTarget.PRODUCTS,
                ),
            )
        }
        return alerts
    }

    /** The deliveries due on each day from [from] to [to], keyed by day. Lines are named by their Kady product when linked. */
    fun arrivals(snapshot: InventorySnapshot?, links: InventoryLinks, products: List<Product>, from: String, to: String): Map<String, List<Arrival>> {
        if (snapshot == null) return emptyMap()
        val names = products.associate { it.id to it.name }
        val out = LinkedHashMap<String, MutableList<Arrival>>()
        for (item in snapshot.items) {
            val productId = links.byKey[item.key]?.takeIf { it in names }
            for (delivery in item.incoming) {
                if (delivery.date < from || delivery.date > to) continue
                out.getOrPut(delivery.date) { ArrayList() }
                    .add(Arrival(if (productId != null) names.getValue(productId) else item.name, productId, delivery.qty, item.unit))
            }
        }
        return out
    }

    /** The products a campaign's posts are about, the ones that ran out first. Nothing is shown without a snapshot. */
    fun campaignRows(posts: List<SavedContent>, stock: Map<String, StockInfo>, hasSnapshot: Boolean): List<CampaignStockRow> {
        if (!hasSnapshot) return emptyList()
        return posts.filter { it.status != ContentStatus.ARCHIVED }
            .distinctBy { it.productId }
            .map { CampaignStockRow(it.productId, it.productName, stock[it.productId]) }
            .sortedWith(compareBy({ it.info?.status?.ordinal ?: Int.MAX_VALUE }, { it.productName }))
    }
}
