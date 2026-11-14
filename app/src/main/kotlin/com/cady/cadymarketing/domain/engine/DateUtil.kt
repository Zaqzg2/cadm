package com.cady.cadymarketing.domain.engine

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Dates are plain "yyyy-MM-dd" strings. java.time is deliberately not used: the app supports Android 7
 * (API 24) and java.time needs API 26 unless core-library desugaring is added.
 */
object DateUtil {
    fun today(): String = format(Calendar.getInstance())

    fun format(calendar: Calendar): String = String.format(
        Locale.US,
        "%04d-%02d-%02d",
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH) + 1,
        calendar.get(Calendar.DAY_OF_MONTH),
    )

    /** The date picker returns UTC midnight milliseconds. */
    fun fromUtcMillis(millis: Long): String {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.timeInMillis = millis
        return format(calendar)
    }

    /** "yyyy-MM-dd" to UTC midnight milliseconds, or null when the text is not a date. */
    fun toUtcMillis(date: String): Long? {
        val parts = date.split('-')
        if (parts.size != 3) return null
        val year = parts[0].toIntOrNull() ?: return null
        val month = parts[1].toIntOrNull() ?: return null
        val day = parts[2].toIntOrNull() ?: return null
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        calendar.clear()
        calendar.set(year, month - 1, day)
        return calendar.timeInMillis
    }
}
