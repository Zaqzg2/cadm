package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Campaign
import com.cady.cadymarketing.domain.model.CampaignStatus
import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.Occasion
import com.cady.cadymarketing.domain.model.OccasionDay
import com.cady.cadymarketing.domain.model.SavedContent

/** Everything that happens on one day of the calendar. */
data class DayEntries(
    val date: String,
    val content: List<SavedContent>,
    val campaigns: List<Campaign>,
    val occasions: List<OccasionDay>,
) {
    val isEmpty: Boolean get() = content.isEmpty() && campaigns.isEmpty() && occasions.isEmpty()
}

/** Puts the saved posts, the campaigns and the occasions on the days of a date range. Pure Kotlin. */
object CalendarEntries {
    /**
     * One entry per day from [from] to [to] (empty days included, so the screen can look any day up).
     * Posts appear on the day they are scheduled for (archived ones do not); a campaign appears on every day its dates
     * cover; an ended campaign only on days up to [today], so its unfinished plan does not look like it is still running.
     */
    fun build(
        from: String,
        to: String,
        content: List<SavedContent>,
        campaigns: List<Campaign>,
        occasions: List<Occasion>,
        today: String,
    ): Map<String, DayEntries> {
        val days = CalendarMath.datesBetween(from, to)
        if (days.isEmpty()) return emptyMap()
        val byDate = content
            .filter { it.status != ContentStatus.ARCHIVED }
            .filter { it.scheduledDate != null }
            .groupBy { it.scheduledDate!! }
        val builtIn = days.mapNotNull { CalendarMath.yearOf(it) }.distinct()
            .flatMap { HijriCalendar.occasions(it) }
            .groupBy { it.date }
        val result = LinkedHashMap<String, DayEntries>()
        for (day in days) {
            val posts = byDate[day].orEmpty().sortedBy { it.scheduledAt }
            val running = campaigns.filter { campaign ->
                campaign.coversDate(day) && (campaign.status != CampaignStatus.ENDED || day <= today)
            }
            val own = occasions
                .filter { it.date == day || (it.yearly && it.date.drop(5) == day.drop(5)) }
                .map { OccasionDay(title = it.title, date = day, builtIn = false, id = it.id) }
            result[day] = DayEntries(day, posts, running, own + builtIn[day].orEmpty())
        }
        return result
    }
}
