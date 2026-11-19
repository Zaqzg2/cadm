package com.cady.cadymarketing.domain.model

/** Where an order that carries a campaign code was read from. */
enum class OrderOrigin(val code: String, val label: String) {
    SITE("site", "طلبات الموقع"),
    KADY("kady", "فواتير كادي"),
    ;

    companion object {
        fun fromCode(code: String?): OrderOrigin = entries.firstOrNull { it.code == code } ?: SITE
    }
}

/**
 * An order that carries a campaign code, as read from the website or from Kady. [amount] is whole riyals when the order
 * had a total; [date] is "yyyy-MM-dd" when it had one; a [cancelled] order is kept but never counted.
 */
data class AttributedOrder(
    val id: String,
    val origin: OrderOrigin,
    val campaignCode: String,
    val channel: Channel?,
    val amount: Long?,
    val date: String?,
    val cancelled: Boolean,
)

/** What the manager can write down by hand for a campaign (the things no system counts for them). */
enum class ResultMetric(val code: String, val label: String, val unit: String) {
    ORDERS("o", "طلبات", "طلب"),
    SALES("s", "مبيعات", "ريال"),
    REACH("r", "وصول ومشاهدات", "مشاهدة"),
    REPLIES("p", "ردود ورسائل", "رد"),
    ;

    companion object {
        fun fromCode(code: String?): ResultMetric = entries.firstOrNull { it.code == code } ?: ORDERS
    }
}

/** One result written by hand: a number for a campaign on a day, optionally for one channel. */
data class ManualResult(
    val id: String,
    val campaignId: String,
    val channel: Channel?,
    val metric: ResultMetric,
    val value: Long,
    val date: String,
    val note: String,
    val createdAt: Long,
    val updatedAt: Long,
)

/** The orders and sales of one channel ([channel] is null for orders that did not say where they came from). */
data class ChannelStat(val channel: Channel?, val orders: Int, val revenue: Long)

data class CampaignReport(
    val campaign: Campaign,
    val posts: Int,
    val published: Int,
    val siteOrders: Int,
    val kadyOrders: Int,
    val autoRevenue: Long,
    val manualOrders: Long,
    val manualSales: Long,
    val reach: Long,
    val replies: Long,
    val cancelled: Int,
    val byChannel: List<ChannelStat>,
) {
    /** Orders counted by the systems plus orders written by hand. */
    val orders: Long get() = siteOrders.toLong() + kadyOrders.toLong() + manualOrders

    val revenue: Long get() = autoRevenue + manualSales

    val hasAnyResult: Boolean get() = orders > 0L || revenue > 0L || reach > 0L || replies > 0L
}

/** All campaigns for one period, and what could not be placed. */
data class ReportSummary(
    val campaigns: List<CampaignReport>,
    val orders: Long,
    val revenue: Long,
    val cancelled: Int,
    /** Orders whose code matches no campaign here (a mistyped code, or a campaign that was deleted). */
    val orphanOrders: Int,
    val orphanCodes: List<String>,
    val bestChannel: Channel?,
    val publishedPosts: Int,
)
