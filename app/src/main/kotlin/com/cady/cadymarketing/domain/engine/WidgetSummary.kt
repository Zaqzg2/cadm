package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Campaign
import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.SavedContent

/** The next post that has a time, for the widget's subtitle: what it is about and when ("اليوم 14:30"). */
data class WidgetNext(val title: String, val whenLabel: String)

/** What the home-screen widget shows, worked out from what is stored. */
data class WidgetSummary(
    val dateLabel: String,
    val runningCampaigns: Int,
    val drafts: Int,
    val pending: Int,
    val approved: Int,
    val scheduledNextWeek: Int,
    /** Posts whose time has passed and that are not published yet. */
    val late: Int,
    val next: WidgetNext?,
)

/** The widget's numbers. They agree with the home screen's because they come from the same engine. Pure Kotlin. */
object WidgetSummaryEngine {
    private val open = setOf(ContentStatus.DRAFT, ContentStatus.PENDING, ContentStatus.APPROVED, ContentStatus.RETURNED)

    /** [nowMinute] is "yyyy-MM-ddTHH:mm", the same shape as a post's schedule, so the two compare as text. */
    fun compute(content: List<SavedContent>, campaigns: List<Campaign>, today: String, nowMinute: String): WidgetSummary {
        val stats = HomeInsightsEngine.compute(content, campaigns, emptyList(), today, nowMinute).stats
        val scheduled = content.filter { post -> post.status in open && post.scheduledAt != null }
        val late = scheduled.count { post -> post.scheduledAt.orEmpty() < nowMinute }
        val next = scheduled
            .filter { post -> post.scheduledAt.orEmpty() >= nowMinute }
            .minByOrNull { post -> post.scheduledAt.orEmpty() }
        return WidgetSummary(
            dateLabel = CalendarMath.longDate(today),
            runningCampaigns = stats.runningCampaigns,
            drafts = stats.drafts,
            pending = stats.pending,
            approved = stats.approved,
            scheduledNextWeek = stats.scheduledNextWeek,
            late = late,
            next = next?.let { post -> WidgetNext(post.productName, whenLabel(post.scheduledAt.orEmpty(), today)) },
        )
    }

    /** "اليوم 14:30", "غدًا 09:00", or the weekday and the day ("الأربعاء 11-26 09:00"). */
    fun whenLabel(scheduledAt: String, today: String): String {
        val date = scheduledAt.substringBefore('T')
        val time = if (scheduledAt.contains('T')) scheduledAt.substringAfter('T').take(TIME_LENGTH) else ""
        val day = when (date) {
            today -> "اليوم"
            CalendarMath.addDays(today, 1) -> "غدًا"
            else -> (CalendarMath.weekdayName(date) + " " + date.drop(DATE_PREFIX)).trim()
        }
        return (day + " " + time).trim()
    }

    private const val TIME_LENGTH = 5
    private const val DATE_PREFIX = 5
}
