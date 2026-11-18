package com.cady.cadymarketing.domain.model

/** Where a campaign stands. The codes are the ones stored in the database. */
enum class CampaignStatus(val code: String, val label: String) {
    ACTIVE("a", "جارية"),
    PAUSED("p", "متوقفة"),
    ENDED("e", "منتهية"),
    ;

    companion object {
        fun fromCode(code: String?): CampaignStatus = entries.firstOrNull { it.code == code } ?: ACTIVE
    }
}

/** What a campaign is doing on a given day, worked out from its status and its dates. */
enum class CampaignPhase(val label: String) {
    UPCOMING("لم تبدأ بعد"),
    RUNNING("جارية"),

    /** Still marked as running although its end date has passed: the manager should end it. */
    OVERDUE("تجاوزت موعد نهايتها"),
    PAUSED("متوقفة"),
    ENDED("منتهية"),
}

/**
 * One marketing campaign. Dates are "yyyy-MM-dd" or null (open on that side).
 * [code] is carried by every link of the campaign (?c=CODE) so orders can be traced back; it never
 * changes, so links that were already shared keep working. [number] (CMP-0001) is assigned at the
 * first sync, so it is null until then.
 */
data class Campaign(
    val id: String,
    val code: String,
    val number: String?,
    val name: String,
    val goal: String,
    val startDate: String?,
    val endDate: String?,
    val status: CampaignStatus,
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun phase(today: String): CampaignPhase = when (status) {
        CampaignStatus.ENDED -> CampaignPhase.ENDED
        CampaignStatus.PAUSED -> CampaignPhase.PAUSED
        CampaignStatus.ACTIVE -> when {
            startDate != null && today < startDate -> CampaignPhase.UPCOMING
            endDate != null && today > endDate -> CampaignPhase.OVERDUE
            else -> CampaignPhase.RUNNING
        }
    }

    /** True when [date] is inside the campaign's dates. A campaign with no dates at all covers nothing. */
    fun coversDate(date: String): Boolean {
        if (startDate == null && endDate == null) return false
        return (startDate == null || startDate <= date) && (endDate == null || endDate >= date)
    }

    /** "2026-11-01 ← 2026-11-30", "من 2026-11-01", "حتى 2026-11-30" or "بلا تواريخ". */
    val periodText: String
        get() = when {
            startDate != null && endDate != null -> "$startDate ← $endDate"
            startDate != null -> "من $startDate"
            endDate != null -> "حتى $endDate"
            else -> "بلا تواريخ"
        }
}
