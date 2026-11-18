package com.cady.cadymarketing.domain.engine

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** One square of the month grid: a day, and whether it belongs to the month being shown (or to its neighbours). */
data class CalendarCell(val date: String, val inMonth: Boolean)

/**
 * Date arithmetic for the calendar. Dates are plain "yyyy-MM-dd" strings and every calculation runs in UTC, so a
 * daylight-saving change can never move a day. The week starts on Saturday, as Yemeni calendars do.
 * java.time is deliberately not used (the app supports Android 7).
 */
object CalendarMath {
    val monthNames: List<String> = listOf(
        "يناير", "فبراير", "مارس", "أبريل", "مايو", "يونيو",
        "يوليو", "أغسطس", "سبتمبر", "أكتوبر", "نوفمبر", "ديسمبر",
    )

    /** Saturday first. */
    val weekdayNames: List<String> = listOf("السبت", "الأحد", "الاثنين", "الثلاثاء", "الأربعاء", "الخميس", "الجمعة")

    private const val MILLIS_PER_DAY = 86_400_000L

    private fun utc(): Calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear() }

    fun date(year: Int, month: Int, day: Int): String =
        String.format(Locale.US, "%04d-%02d-%02d", year, month, day)

    fun yearOf(date: String): Int? = date.take(4).toIntOrNull()

    fun monthOf(date: String): Int? = date.drop(5).take(2).toIntOrNull()

    fun dayOf(date: String): Int? = date.drop(8).take(2).toIntOrNull()

    /** Days since 1970-01-01, or null when the text is not a date. */
    fun dayNumber(date: String): Long? = DateUtil.toUtcMillis(date)?.let { Math.floorDiv(it, MILLIS_PER_DAY) }

    fun addDays(date: String, days: Int): String {
        val millis = DateUtil.toUtcMillis(date) ?: return date
        return DateUtil.fromUtcMillis(millis + days * MILLIS_PER_DAY)
    }

    /** Whole days from [from] to [to] (negative when [to] is earlier), or null when either is not a date. */
    fun daysBetween(from: String, to: String): Int? {
        val a = dayNumber(from) ?: return null
        val b = dayNumber(to) ?: return null
        return (b - a).toInt()
    }

    /** 0 for Saturday up to 6 for Friday, or null when the text is not a date. */
    fun weekdayIndex(date: String): Int? {
        val millis = DateUtil.toUtcMillis(date) ?: return null
        val calendar = utc().apply { timeInMillis = millis }
        return (calendar.get(Calendar.DAY_OF_WEEK) - Calendar.SATURDAY + 7) % 7
    }

    fun weekdayName(date: String): String = weekdayIndex(date)?.let { weekdayNames[it] }.orEmpty()

    /** "الاثنين 16 نوفمبر 2026". */
    fun longDate(date: String): String {
        val year = yearOf(date)
        val month = monthOf(date)
        val day = dayOf(date)
        if (year == null || month == null || day == null || month !in 1..12) return date
        return weekdayName(date) + " " + day + " " + monthNames[month - 1] + " " + year
    }

    fun monthTitle(year: Int, month: Int): String = monthNames[month - 1] + " " + year

    /** The month [delta] months after (or before) the given one, as year to month. */
    fun addMonths(year: Int, month: Int, delta: Int): Pair<Int, Int> {
        val index = year * 12 + (month - 1) + delta
        return Math.floorDiv(index, 12) to (Math.floorMod(index, 12) + 1)
    }

    fun daysInMonth(year: Int, month: Int): Int {
        val calendar = utc().apply { set(year, month - 1, 1) }
        return calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    /** The first and last day of a month. */
    fun monthRange(year: Int, month: Int): Pair<String, String> =
        date(year, month, 1) to date(year, month, daysInMonth(year, month))

    /**
     * The weeks of a month, Saturday first, padded with the days of the neighbouring months so every week is
     * complete. Always a multiple of seven cells (five or six weeks).
     */
    fun monthGrid(year: Int, month: Int): List<CalendarCell> {
        val first = date(year, month, 1)
        val lead = weekdayIndex(first) ?: 0
        val total = daysInMonth(year, month)
        val weeks = (lead + total + 6) / 7
        val start = addDays(first, -lead)
        return List(weeks * 7) { index ->
            val day = addDays(start, index)
            CalendarCell(day, monthOf(day) == month && yearOf(day) == year)
        }
    }

    /** Every date from [from] to [to], both included (at most [limit] of them, a guard against a wrong range). */
    fun datesBetween(from: String, to: String, limit: Int = 400): List<String> {
        val count = daysBetween(from, to) ?: return emptyList()
        if (count < 0) return emptyList()
        return List(minOf(count + 1, limit)) { addDays(from, it) }
    }
}
