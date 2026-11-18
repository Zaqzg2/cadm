package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Channel
import kotlin.random.Random

/** One tracking link of a campaign: the link a post on [channel] carries. */
data class TrackingLink(val channel: Channel, val url: String)

/** Creating and checking campaigns. Pure Kotlin. */
object CampaignRules {
    /** No I, O, 0 or 1: a code is read aloud and typed by hand, so look-alike characters are left out. */
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val CODE_LENGTH = 5
    private const val MAX_TRIES = 200

    /** A new short code that is not in [taken] (the codes of every campaign ever made, deleted ones included). */
    fun newCode(taken: Set<String>, random: Random = Random.Default): String {
        var length = CODE_LENGTH
        while (true) {
            repeat(MAX_TRIES) {
                val code = buildString { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
                if (code !in taken) return code
            }
            length++
        }
    }

    /** True for a real calendar day written as "yyyy-MM-dd" ("2026-02-30" is not one). */
    fun isDate(text: String?): Boolean {
        if (text == null || text.length != 10) return false
        val millis = DateUtil.toUtcMillis(text) ?: return false
        return DateUtil.fromUtcMillis(millis) == text
    }

    /** What is wrong with a campaign form, one sentence each. Empty means it can be saved. */
    fun validate(name: String, startDate: String?, endDate: String?): List<String> {
        val problems = ArrayList<String>()
        if (name.isBlank()) problems.add("اكتب اسم الحملة")
        if (startDate != null && !isDate(startDate)) problems.add("تاريخ البداية غير صالح")
        if (endDate != null && !isDate(endDate)) problems.add("تاريخ النهاية غير صالح")
        if (startDate != null && endDate != null && isDate(startDate) && isDate(endDate) && endDate < startDate) {
            problems.add("تاريخ النهاية قبل تاريخ البداية")
        }
        return problems
    }

    /** The link of every channel for this campaign code. */
    fun trackingLinks(brand: Brand, code: String): List<TrackingLink> =
        Channel.entries.map { TrackingLink(it, LinkBuilder.build(brand, it, code)) }
}
