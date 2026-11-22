package com.cady.cadymarketing.domain.model

/** One delivery the inventory app expects: the day ("yyyy-MM-dd") and how many units. */
data class IncomingStock(val date: String, val qty: Long)

/**
 * One stock line of the inventory snapshot. There is no price or cost here, on purpose: the inventory app has none,
 * and prices come from Kady. [key] is the identity of the line: barcode, else item number, else the normalised name.
 */
data class InventoryItem(
    val key: String,
    val name: String,
    val barcode: String = "",
    val itemNo: String = "",
    val category: String = "",
    val unit: String = "",
    val onHand: Long = 0L,
    /** The level at which the inventory app says to order again; 0 when it does not say. */
    val reorderAt: Long = 0L,
    /** "yyyy-MM" the goals are for, or empty. */
    val goalMonth: String = "",
    /** The month's goal tiers (up to three) and what has been reached so far. */
    val goalTiers: List<Long> = emptyList(),
    val achieved: Long = 0L,
    val incoming: List<IncomingStock> = emptyList(),
    /** When the stock was last counted (epoch milliseconds), or 0. */
    val countedAt: Long = 0L,
)

/** What the inventory app exported at [createdAt] (epoch milliseconds). */
data class InventorySnapshot(
    val createdAt: Long,
    val branch: String,
    val items: List<InventoryItem>,
)

enum class StockStatus(val label: String) {
    OUT("نفد"),
    LOW("منخفض"),
    OK("متوفر"),
    OVER("فائض"),
}

/** Where the month's goal stands: the tier being worked toward (1 to 3), its target, and what is reached. */
data class GoalProgress(val tier: Int, val target: Long, val achieved: Long, val reached: Boolean)

/** What the marketing screens show about one linked product. */
data class StockInfo(
    val key: String,
    val itemName: String,
    val unit: String,
    val status: StockStatus,
    val onHand: Long,
    val goal: GoalProgress?,
    val nextArrival: IncomingStock?,
)

/** The links the manager confirmed: an inventory line's key to a Kady product id. Nothing is ever linked by similarity alone. */
data class InventoryLinks(val byKey: Map<String, String> = emptyMap())
