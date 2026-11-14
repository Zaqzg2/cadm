package com.cady.cadymarketing.domain.engine

import java.net.URLEncoder
import java.util.Locale

/** Small text helpers shared by the engines. Pure Kotlin, no Android types. */
object ArabicText {
    private const val ARABIC_INDIC = "٠١٢٣٤٥٦٧٨٩"
    private const val EXTENDED_INDIC = "۰۱۲۳۴۵۶۷۸۹"

    /**
     * Arabic-Indic digits to western digits, so a number cannot hide behind the other script.
     * The original tool converted only the Arabic-Indic set; the Persian set is added here.
     * The length never changes, so positions in the result match positions in the input.
     */
    fun toWesternDigits(text: String): String {
        val out = StringBuilder(text.length)
        for (c in text) {
            val arabic = ARABIC_INDIC.indexOf(c)
            val extended = EXTENDED_INDIC.indexOf(c)
            out.append(
                when {
                    arabic >= 0 -> '0' + arabic
                    extended >= 0 -> '0' + extended
                    else -> c
                },
            )
        }
        return out.toString()
    }

    /** 6800 becomes "6,800" (the original tool used toLocaleString('en')). */
    fun formatNumber(value: Long): String = String.format(Locale.US, "%,d", value)

    /** Same result as JavaScript's encodeURIComponent. */
    fun encodeUriComponent(value: String): String =
        URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("%21", "!")
            .replace("%27", "'")
            .replace("%28", "(")
            .replace("%29", ")")
            .replace("%7E", "~")

    /** "007" becomes "7" and "000" becomes "0" (what parseInt then String does in the original tool). */
    fun stripLeadingZeros(digits: String): String = digits.trimStart('0').ifEmpty { "0" }
}
