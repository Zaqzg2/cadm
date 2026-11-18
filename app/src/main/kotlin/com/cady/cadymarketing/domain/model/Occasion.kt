package com.cady.cadymarketing.domain.model

/** An occasion the manager added to the calendar. [date] is "yyyy-MM-dd"; a [yearly] one repeats on the same day every year. */
data class Occasion(
    val id: String,
    val title: String,
    val date: String,
    val yearly: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
)

/** An occasion on one day of the calendar: the manager's own, or a built-in one ([id] is null for those). */
data class OccasionDay(
    val title: String,
    val date: String,
    val builtIn: Boolean,
    val id: String? = null,
)
