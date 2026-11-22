package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.MktRole
import com.cady.cadymarketing.domain.model.SavedContent
import java.util.Calendar
import java.util.TimeZone

/** A reminder to show when an approved post is due: [atMillis] is the moment, [scheduledAt] the schedule it was made for. */
data class Reminder(
    val contentId: String,
    val atMillis: Long,
    val scheduledAt: String,
    val title: String,
    val text: String,
)

/** Which posts get a reminder and when. Pure Kotlin. */
object ReminderRules {
    private val schedulePattern = Regex("^(\\d{4})-(\\d{2})-(\\d{2})(?:T(\\d{2}):(\\d{2}))?$")

    /** A post scheduled for a day without a time is due at this hour. */
    const val DEFAULT_HOUR = 9

    /** Reminders are made at most this far ahead; later ones are made when the date comes closer. */
    const val MAX_AHEAD_MS = 366L * 24L * 60L * 60L * 1000L

    const val TITLE = "حان موعد النشر"

    /** A post's schedule ("yyyy-MM-ddTHH:mm", or just the day) as a moment in [zone]; null when it is not a real date and time. */
    fun parse(scheduledAt: String?, zone: TimeZone = TimeZone.getDefault()): Long? {
        val match = schedulePattern.matchEntire(scheduledAt?.trim() ?: return null) ?: return null
        val year = match.groupValues[1].toInt()
        val month = match.groupValues[2].toInt()
        val day = match.groupValues[3].toInt()
        val hour = match.groupValues[4].toIntOrNull() ?: DEFAULT_HOUR
        val minute = match.groupValues[5].toIntOrNull() ?: 0
        if (month !in 1..12 || day !in 1..31 || hour !in 0..23 || minute !in 0..59) return null
        val calendar = Calendar.getInstance(zone)
        calendar.isLenient = false
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute, 0)
        return try {
            calendar.timeInMillis
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** The reminders that should exist now: one per approved post whose schedule is still ahead, soonest first. */
    fun desired(posts: List<SavedContent>, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): List<Reminder> =
        posts.asSequence()
            .filter { it.status == ContentStatus.APPROVED }
            .mapNotNull { post ->
                val schedule = post.scheduledAt ?: return@mapNotNull null
                val at = parse(schedule, zone) ?: return@mapNotNull null
                if (at <= nowMillis || at - nowMillis > MAX_AHEAD_MS) return@mapNotNull null
                Reminder(
                    contentId = post.id,
                    atMillis = at,
                    scheduledAt = schedule,
                    title = TITLE,
                    text = "«${post.productName}» · ${post.channel.label}",
                )
            }
            .sortedBy { it.atMillis }
            .toList()

    fun delayMillis(reminder: Reminder, nowMillis: Long): Long = (reminder.atMillis - nowMillis).coerceAtLeast(0L)

    fun workName(contentId: String): String = "reminder-$contentId"

    /** True when the reminder made for [scheduledAt] still applies: the post exists, is still approved and still has that schedule. */
    fun stillDue(post: SavedContent?, scheduledAt: String): Boolean =
        post != null && post.status == ContentStatus.APPROVED && post.scheduledAt == scheduledAt
}

/** What to tell the person: a title, a line, and the keys of the posts it is about (so none is told twice). */
data class Notice(val title: String, val text: String, val keys: List<String>)

/** The periodic check's messages. Pure Kotlin. */
object NotificationRules {
    const val MAX_KEYS = 200

    /** A post in the state it is in now: a post changed again counts as new. */
    fun key(post: SavedContent): String = post.id + ":" + post.updatedAt

    /**
     * The notice for [role] given the posts and the keys already told, or null when nothing is new.
     * The manager is told about posts waiting for approval; a marketer about posts sent back to her.
     */
    fun forRole(role: MktRole, posts: List<SavedContent>, told: Set<String>): Notice? {
        val waiting = when (role) {
            MktRole.MANAGER -> ContentStatus.PENDING
            MktRole.MARKETER -> ContentStatus.RETURNED
            MktRole.NONE -> return null
        }
        val fresh = posts.filter { it.status == waiting && key(it) !in told }
        if (fresh.isEmpty()) return null
        val manager = role == MktRole.MANAGER
        val title = if (manager) "بانتظار موافقتك" else "أُرجع إليك للتعديل"
        val text = if (fresh.size == 1) {
            val post = fresh[0]
            val note = post.note?.takeIf { !manager && it.isNotBlank() }
            if (manager) "منشور «${post.productName}» ينتظر اعتمادك" else "«${post.productName}»" + (note?.let { ": $it" } ?: "")
        } else {
            if (manager) "${fresh.size} منشورات تنتظر اعتمادك" else "${fresh.size} منشورات أُرجعت إليك للتعديل"
        }
        return Notice(title, text, fresh.map { key(it) })
    }

    /** The keys to remember after telling: the old ones then the new ones, no repeats, the latest [MAX_KEYS]. */
    fun remember(told: Collection<String>, newKeys: Collection<String>): List<String> =
        (told + newKeys).distinct().takeLast(MAX_KEYS)
}
