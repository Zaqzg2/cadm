package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.IssueKind
import com.cady.cadymarketing.domain.model.Severity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the checker does beyond the original tool. The original rules are covered by GoldenParityTest. */
class CheckerImprovementsTest {
    private val brand = TestFixtures.brand
    private val product = TestFixtures.offer
    private val today = "2026-11-12"

    private fun check(text: String, channel: Channel? = null) =
        ClaimChecker.check(text, product, brand, channel, today)

    @Test
    fun persianDigitsCannotHideANumber() {
        val result = check("خصم ۵۵ ريال https://x.y/z")
        assertEquals(listOf("رقم ليس في الكتالوج: 55"), result.errors.map { it.message })
    }

    @Test
    fun warningsNeverBlockApproval() {
        val result = check("نص بلا رابط")
        assertTrue(result.passed)
        assertEquals(listOf(IssueKind.MISSING_LINK), result.warnings.map { it.kind })
        assertTrue(result.warnings.all { it.severity == Severity.WARNING })
    }

    @Test
    fun aTextWithALinkHasNoMissingLinkWarning() {
        val result = check("اطلب https://wa.me/967730487946")
        assertTrue(result.issues.none { it.kind == IssueKind.MISSING_LINK })
    }

    @Test
    fun longTextForAStatusIsAdvisedToBeShortened() {
        val longText = "كلمة ".repeat(80) + "https://x.y"
        val status = check(longText, Channel.WHATSAPP_STATUS)
        assertTrue(status.warnings.any { it.kind == IssueKind.TOO_LONG })
        val broadcast = check(longText, Channel.WHATSAPP)
        assertTrue(broadcast.warnings.none { it.kind == IssueKind.TOO_LONG })
    }

    @Test
    fun manyEmojiAreAdvisedAgainst() {
        val result = check("🔥🔥🔥🔥🔥🔥🔥 https://x.y")
        assertTrue(result.warnings.any { it.kind == IssueKind.TOO_MANY_EMOJI })
        assertTrue(check("🔥 https://x.y").warnings.none { it.kind == IssueKind.TOO_MANY_EMOJI })
    }

    @Test
    fun theCustomerNamePlaceholderIsAllowedButOthersAreFlagged() {
        val ok = check("أهلًا {اسم العميل} https://x.y")
        assertTrue(ok.warnings.none { it.kind == IssueKind.UNKNOWN_PLACEHOLDER })
        val bad = check("أهلًا {شيء} https://x.y")
        assertEquals(listOf("{شيء}"), bad.warnings.filter { it.kind == IssueKind.UNKNOWN_PLACEHOLDER }.map { it.match })
    }

    @Test
    fun highlightsPointAtTheOffendingTextAndSkipLinks() {
        val text = "خصم 55 ريال https://x.y/?c=55 ويقتل"
        val result = check(text)
        val ranges = ClaimChecker.highlights(text, result.errors)
        val highlighted = ranges.map { text.substring(it.first, it.last + 1) }
        assertEquals(listOf("55", "يقتل"), highlighted)
    }

    @Test
    fun aNumberInsideALongerNumberIsNotHighlighted() {
        val text = "رقم 1555 وخصم 55"
        val result = check(text)
        val ranges = ClaimChecker.highlights(text, result.errors.filter { it.match == "55" })
        assertEquals(listOf("55"), ranges.map { text.substring(it.first, it.last + 1) })
        assertEquals(text.indexOf("وخصم") + 5, ranges.single().first)
    }

    @Test
    fun fixRemovesTheOffendingText() {
        val text = "ينظف يقتل البقع بسرعة"
        val issue = check(text).errors.first { it.match == "يقتل" }
        assertEquals("ينظف البقع بسرعة", ClaimChecker.fix(text, issue))
    }

    @Test
    fun snippetSafetyIgnoresTheOfferDateButNotClaims() {
        val expired = TestFixtures.expiredOffer
        assertTrue(ClaimChecker.isSafeSnippet("عبوة عملية", expired, brand))
        assertFalse(ClaimChecker.isSafeSnippet("يقتل الجراثيم", expired, brand))
        assertFalse(ClaimChecker.isSafeSnippet("خلطة بـ 99 مكوّنًا", expired, brand))
        assertTrue(ClaimChecker.isSafeSnippet("عبوة بحجم 5 لتر", expired, brand))
    }
}
