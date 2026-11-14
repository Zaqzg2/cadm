package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.CheckResult
import com.cady.cadymarketing.domain.model.Issue
import com.cady.cadymarketing.domain.model.IssueKind
import com.cady.cadymarketing.domain.model.Product
import com.cady.cadymarketing.domain.model.Severity

/**
 * Checks a text against the catalogue before it can be approved.
 *
 * ERROR rules are exactly the rules (and messages) of the original tool, in the same order:
 * empty text, expired offer, banned phrase, and any number that is not in the catalogue.
 * WARNING rules are additions: they advise, they never block.
 */
object ClaimChecker {
    private val urlRegex = Regex("https?://\\S+")
    private val thousandsRegex = Regex("(\\d),(?=\\d{3})")
    private val digitsRegex = Regex("\\d+")
    private val placeholderRegex = Regex("\\{(.+?)\\}")
    private const val MAX_EMOJI = 6

    /** Digits to western, links removed (their numbers are not claims), thousands separators removed. */
    fun normalize(text: String): String =
        ArabicText.toWesternDigits(text).replace(urlRegex, " ").replace(thousandsRegex, "\$1")

    fun check(
        text: String,
        product: Product,
        brand: Brand,
        channel: Channel? = null,
        today: String = DateUtil.today(),
    ): CheckResult {
        val issues = ArrayList<Issue>()
        val normalized = normalize(text)

        if (text.isBlank()) {
            issues.add(Issue(Severity.ERROR, IssueKind.EMPTY, "النص فارغ"))
        }
        if (product.hasOffer && product.offerEnd.isNotEmpty() && product.offerEnd < today) {
            issues.add(Issue(Severity.ERROR, IssueKind.OFFER_EXPIRED, "انتهى العرض في ${product.offerEnd}"))
        }
        for (word in brand.bannedList) {
            if (normalized.contains(word)) {
                issues.add(Issue(Severity.ERROR, IssueKind.BANNED_PHRASE, "عبارة ممنوعة: $word", word))
            }
        }
        val allowed = allowedNumbers(product)
        val phone = brand.whatsapp.filter { it in '0'..'9' }
        for (found in digitsRegex.findAll(normalized)) {
            val digits = found.value
            if (ArabicText.stripLeadingZeros(digits) !in allowed && !phone.contains(digits)) {
                issues.add(Issue(Severity.ERROR, IssueKind.UNKNOWN_NUMBER, "رقم ليس في الكتالوج: $digits", digits))
            }
        }

        issues.addAll(advisories(text, channel))
        return CheckResult(issues)
    }

    /** The numbers a text may contain: the prices, the saving, and the numbers inside the unit and the offer date. */
    fun allowedNumbers(product: Product): Set<String> {
        val allowed = HashSet<String>()
        allowed.add(product.price.toString())
        product.offerPrice?.let { allowed.add(it.toString()) }
        if (product.hasOffer) allowed.add(product.saving.toString())
        val extra = ArabicText.toWesternDigits(product.unit + " " + product.offerEnd)
        for (found in digitsRegex.findAll(extra)) {
            allowed.add(ArabicText.stripLeadingZeros(found.value))
        }
        return allowed
    }

    /** True when a snippet (a feature line, a vocabulary line) holds no banned phrase and no foreign number. */
    fun isSafeSnippet(snippet: String, product: Product, brand: Brand): Boolean =
        check(snippet, product, brand).issues.none {
            it.kind == IssueKind.BANNED_PHRASE || it.kind == IssueKind.UNKNOWN_NUMBER
        }

    /** Character ranges of the offending text, for highlighting. Numbers inside links are skipped. */
    fun highlights(text: String, issues: List<Issue>): List<IntRange> {
        val western = ArabicText.toWesternDigits(text)
        val urlRanges = urlRegex.findAll(western).map { it.range }.toList()
        val found = ArrayList<IntRange>()
        for (issue in issues) {
            val needle = issue.match
            if (needle.isNullOrEmpty()) continue
            var from = 0
            while (from <= western.length - needle.length) {
                val at = western.indexOf(needle, from)
                if (at < 0) break
                val range = at until (at + needle.length)
                from = at + needle.length
                if (issue.kind == IssueKind.UNKNOWN_NUMBER) {
                    val before = western.getOrNull(at - 1)
                    val after = western.getOrNull(at + needle.length)
                    if (before != null && before.isDigit()) continue
                    if (after != null && after.isDigit()) continue
                    if (urlRanges.any { it.first <= range.last && range.first <= it.last }) continue
                }
                found.add(range)
            }
        }
        return found.sortedBy { it.first }
    }

    /** Removes the offending text of one issue (a banned phrase or a foreign number). */
    fun fix(text: String, issue: Issue): String {
        val ranges = highlights(text, listOf(issue))
        if (ranges.isEmpty()) return text
        val builder = StringBuilder(text)
        for (range in ranges.sortedByDescending { it.first }) {
            builder.delete(range.first, range.last + 1)
        }
        return builder.toString()
            .replace(Regex("[ \\t]{2,}"), " ")
            .replace(Regex(" +\\n"), "\n")
            .trim()
    }

    private fun advisories(text: String, channel: Channel?): List<Issue> {
        if (text.isBlank()) return emptyList()
        val list = ArrayList<Issue>()
        if (!urlRegex.containsMatchIn(ArabicText.toWesternDigits(text))) {
            list.add(Issue(Severity.WARNING, IssueKind.MISSING_LINK, "لا يوجد رابط طلب في النص، فلن يمكن تتبّع الطلبات"))
        }
        if (channel != null && text.length > channel.softLimit) {
            list.add(
                Issue(
                    Severity.WARNING,
                    IssueKind.TOO_LONG,
                    "النص طويل لقناة ${channel.label} (${text.length} حرفًا)، الأفضل اختصاره",
                ),
            )
        }
        if (countEmoji(text) > MAX_EMOJI) {
            list.add(Issue(Severity.WARNING, IssueKind.TOO_MANY_EMOJI, "إيموجي كثيرة، قلّلها ليبدو النص أنظف"))
        }
        for (found in placeholderRegex.findAll(text)) {
            if (found.value != Placeholders.CUSTOMER_NAME) {
                list.add(
                    Issue(Severity.WARNING, IssueKind.UNKNOWN_PLACEHOLDER, "متغير غير معروف في النص: ${found.value}", found.value),
                )
            }
        }
        return list
    }

    private fun countEmoji(text: String): Int {
        var count = 0
        var index = 0
        while (index < text.length) {
            val codePoint = Character.codePointAt(text, index)
            if (codePoint in 0x1F300..0x1FAFF || codePoint in 0x2600..0x27BF || codePoint == 0x2B50) count++
            index += Character.charCount(codePoint)
        }
        return count
    }
}
