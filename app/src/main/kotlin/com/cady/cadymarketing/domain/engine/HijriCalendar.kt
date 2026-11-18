package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.OccasionDay

/**
 * The built-in occasions (Ramadan, the two Eids...) from the tabular Islamic calendar. The tabular calendar is a
 * fixed arithmetic rule, so it can be off by a day from the real moon sighting: the screen labels these as
 * approximate, and the manager can add an exact occasion of their own.
 */
object HijriCalendar {
    private const val ISLAMIC_EPOCH_JDN = 1_948_440L

    /** The Gregorian "yyyy-MM-dd" of an Islamic date (year, month 1..12, day). */
    fun toGregorian(year: Int, month: Int, day: Int): String {
        val monthDays = (59L * (month - 1) + 1L) / 2L
        val jdn = day + monthDays + (year - 1L) * 354L + Math.floorDiv(3L + 11L * year, 30L) + ISLAMIC_EPOCH_JDN - 1L
        return fromJulianDay(jdn)
    }

    private fun fromJulianDay(jdn: Long): String {
        val a = jdn + 32044L
        val b = (4L * a + 3L) / 146097L
        val c = a - 146097L * b / 4L
        val d = (4L * c + 3L) / 1461L
        val e = c - 1461L * d / 4L
        val m = (5L * e + 2L) / 153L
        val day = e - (153L * m + 2L) / 5L + 1L
        val month = m + 3L - 12L * (m / 10L)
        val year = 100L * b + d - 4800L + m / 10L
        return CalendarMath.date(year.toInt(), month.toInt(), day.toInt())
    }

    private class Rule(val title: String, val month: Int, val day: Int)

    private val rules = listOf(
        Rule("رأس السنة الهجرية", 1, 1),
        Rule("المولد النبوي الشريف", 3, 12),
        Rule("بداية شهر رمضان", 9, 1),
        Rule("عيد الفطر", 10, 1),
        Rule("يوم عرفة", 12, 9),
        Rule("عيد الأضحى", 12, 10),
    )

    /** The built-in occasions that fall in the given Gregorian year, in date order. */
    fun occasions(gregorianYear: Int): List<OccasionDay> {
        val approximate = ((gregorianYear - 622) * 1.0307).toInt()
        val found = ArrayList<OccasionDay>()
        for (hijriYear in (approximate - 2)..(approximate + 2)) {
            for (rule in rules) {
                val date = toGregorian(hijriYear, rule.month, rule.day)
                if (CalendarMath.yearOf(date) == gregorianYear) {
                    found.add(OccasionDay(title = rule.title, date = date, builtIn = true))
                }
            }
        }
        return found.sortedBy { it.date }
    }
}
