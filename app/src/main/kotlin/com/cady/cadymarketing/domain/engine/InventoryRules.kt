package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.GoalProgress
import com.cady.cadymarketing.domain.model.IncomingStock
import com.cady.cadymarketing.domain.model.InventoryItem
import com.cady.cadymarketing.domain.model.StockInfo
import com.cady.cadymarketing.domain.model.StockStatus

/** How full or empty a line of the inventory snapshot is, and what to say about it. Pure Kotlin. */
object InventoryRules {
    /** With no reorder level from the inventory app, "low" is this share of the first goal tier. */
    const val LOW_PERCENT_OF_FIRST_TIER = 20

    /** Stock of this many first-tier goals or more is a surplus. */
    const val OVER_MULTIPLE_OF_FIRST_TIER = 2

    /** A goal this close (in percent) to its next tier counts as "near the goal". */
    const val NEAR_GOAL_PERCENT = 80

    /** A snapshot older than this many days is called old. */
    const val STALE_DAYS = 7

    /** The snapshot is stored as one Firestore document (1 MB at most): bigger than this is refused. */
    const val MAX_DOC_BYTES = 900_000

    private const val MS_PER_DAY = 86_400_000L

    /**
     * A name as it is compared: digits western, hamza and dots on alef/ya/ta marbuta unified, marks and tatweel removed,
     * punctuation turned into a space, spaces collapsed, Latin lower-cased. Two names are "the same" only if this is equal.
     */
    fun normalizeName(text: String): String {
        val out = StringBuilder(text.length)
        for (original in ArabicText.toWesternDigits(text)) {
            val ch = when (original) {
                'أ', 'إ', 'آ', 'ٱ' -> 'ا'
                'ى', 'ئ' -> 'ي'
                'ة' -> 'ه'
                'ؤ' -> 'و'
                else -> original
            }
            val code = ch.code
            if (code in 0x064B..0x065F || code == 0x0670 || code == 0x0640) continue
            out.append(if (Character.isLetterOrDigit(ch)) ch.lowercaseChar() else ' ')
        }
        return out.toString().trim().replace(Regex("\\s+"), " ")
    }

    /** The identity of a line, in the inventory app's own order: barcode, then item number, then the name. */
    fun itemKey(barcode: String, itemNo: String, name: String): String = when {
        barcode.isNotBlank() -> "bc:" + barcode.trim()
        itemNo.isNotBlank() -> "no:" + itemNo.trim().lowercase()
        else -> "nm:" + normalizeName(name)
    }

    private fun tiers(item: InventoryItem): List<Long> = item.goalTiers.filter { it > 0L }.sorted()

    fun status(item: InventoryItem): StockStatus {
        if (item.onHand <= 0L) return StockStatus.OUT
        val first = tiers(item).firstOrNull() ?: 0L
        val low = if (item.reorderAt > 0L) item.reorderAt else first * LOW_PERCENT_OF_FIRST_TIER / 100
        return when {
            low > 0L && item.onHand <= low -> StockStatus.LOW
            first > 0L && item.onHand >= first * OVER_MULTIPLE_OF_FIRST_TIER -> StockStatus.OVER
            else -> StockStatus.OK
        }
    }

    /** The tier being worked toward (the first target not yet reached), or the top tier when all are reached; null without goals. */
    fun goal(item: InventoryItem): GoalProgress? {
        val all = tiers(item)
        if (all.isEmpty()) return null
        val index = all.indexOfFirst { it > item.achieved }
        return if (index >= 0) {
            GoalProgress(index + 1, all[index], item.achieved, reached = false)
        } else {
            GoalProgress(all.size, all.last(), item.achieved, reached = true)
        }
    }

    /** The first delivery due on [today] or later. */
    fun nextArrival(item: InventoryItem, today: String): IncomingStock? =
        item.incoming.filter { it.qty > 0L && it.date >= today }.minByOrNull { it.date }

    fun info(item: InventoryItem, today: String): StockInfo = StockInfo(
        key = item.key,
        itemName = item.name,
        unit = item.unit,
        status = status(item),
        onHand = item.onHand,
        goal = goal(item),
        nextArrival = nextArrival(item, today),
    )

    /** True for a surplus, or a goal within reach of its next tier: a post may help. */
    fun needsPromotion(info: StockInfo): Boolean {
        if (info.status == StockStatus.OVER) return true
        val goal = info.goal ?: return false
        return !goal.reached && goal.target > 0L && goal.achieved * 100L >= goal.target * NEAR_GOAL_PERCENT
    }

    /** True for a product that has run out: promoting it is a mistake. */
    fun shouldAvoid(info: StockInfo): Boolean = info.status == StockStatus.OUT

    /** The products the idea of the day leans toward: those in surplus. Home and the Studio both use this, so they agree. */
    fun preferredForIdeas(stock: Map<String, StockInfo>): Set<String> =
        stock.filterValues { it.status == StockStatus.OVER }.keys

    /** Whole days since the snapshot was made, or null when its time is unknown. */
    fun ageDays(createdAt: Long, nowMs: Long): Int? =
        if (createdAt <= 0L) null else ((nowMs - createdAt).coerceAtLeast(0L) / MS_PER_DAY).toInt()

    fun isStale(ageDays: Int?): Boolean = ageDays != null && ageDays > STALE_DAYS

    fun goalText(goal: GoalProgress, unit: String): String {
        val suffix = if (unit.isBlank()) "" else " $unit"
        return if (goal.reached) {
            "تحقق الهدف الأعلى (المستوى ${goal.tier}): ${goal.achieved} من ${goal.target}$suffix"
        } else {
            "الهدف التالي (المستوى ${goal.tier}): ${goal.achieved} من ${goal.target}$suffix"
        }
    }
}

enum class NoteLevel { WARNING, INFO }

data class StockNote(val level: NoteLevel, val text: String)

/** What the Studio says about the product a post is about. It warns; it never blocks. Pure Kotlin. */
object StockWarnings {
    fun note(info: StockInfo?, ageDays: Int?): StockNote? {
        if (info == null) return null
        val stale = if (InventoryRules.isStale(ageDays)) " (آخر لقطة قبل $ageDays يومًا)" else ""
        val unit = if (info.unit.isBlank()) "" else " ${info.unit}"
        return when (info.status) {
            StockStatus.OUT -> {
                val arrival = info.nextArrival?.let { "، وتصل ${it.qty}$unit في ${it.date}" }.orEmpty()
                StockNote(NoteLevel.WARNING, "نفد «${info.itemName}» من المخزون$arrival$stale")
            }
            StockStatus.LOW -> StockNote(NoteLevel.WARNING, "مخزون «${info.itemName}» منخفض: ${info.onHand}$unit$stale")
            StockStatus.OVER -> StockNote(NoteLevel.INFO, "«${info.itemName}» فائض في المخزون (${info.onHand}$unit): ترويجه مناسب$stale")
            StockStatus.OK -> null
        }
    }
}
