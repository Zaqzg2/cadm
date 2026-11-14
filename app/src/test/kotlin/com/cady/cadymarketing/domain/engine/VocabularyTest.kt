package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Channel
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VocabularyTest {
    private val brand = TestFixtures.brand
    private val known = setOf("الاسم", "الوحدة", "السعر", "السعر_قبل", "الفرق", "الانتهاء", "الرابط", "العلامة")
    private val token = Regex("\\{(.+?)\\}")

    private fun lines(): List<Pair<String, String>> {
        val all = ArrayList<Pair<String, String>>()
        for ((tone, v) in DefaultVocabulary.tones) {
            for (line in v.hooks + v.prices + v.offers + v.calls + v.urgency + v.questions) all.add("tone $tone" to line)
        }
        for ((angle, ideas) in DefaultIdeas.byAngle) for (line in ideas) all.add("idea $angle" to line)
        for (t in DefaultVocabulary.templates) all.add("template ${t.name}" to t.text)
        return all
    }

    @Test
    fun noDefaultLineHasADigitABannedPhraseOrAnUnknownPlaceholder() {
        for ((where, line) in lines()) {
            assertTrue("$where: digit in «$line»", line.none { it.isDigit() || it in "٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹" })
            for (word in brand.bannedList) assertTrue("$where: banned «$word» in «$line»", !line.contains(word))
            for (found in token.findAll(line)) assertTrue("$where: unknown {${found.groupValues[1]}} in «$line»", found.groupValues[1] in known)
        }
    }

    @Test
    fun everyToneHasTheSlotsTheGeneratorNeeds() {
        for ((tone, v) in DefaultVocabulary.tones) {
            assertTrue("$tone hooks", v.hooks.size >= 4)
            assertTrue("$tone prices", v.prices.isNotEmpty())
            assertTrue("$tone offers", v.offers.isNotEmpty())
            assertTrue("$tone calls", v.calls.isNotEmpty())
            assertTrue("$tone urgency", v.urgency.isNotEmpty())
            assertTrue("$tone questions", v.questions.isNotEmpty())
            assertTrue("$tone: every call line ends with the link", v.calls.all { it.trim().endsWith("{الرابط}") })
            assertTrue("$tone: every offer line carries a price", v.offers.all { it.contains("{السعر}") })
        }
        assertEquals(setOf("ودّي", "رسمي", "حماسي", "تجاري"), DefaultVocabulary.tones.keys)
    }

    @Test
    fun theOriginalFiveAnglesAreKeptAndMoreAreAdded() {
        val angles = DefaultIdeas.byAngle.keys
        assertTrue(angles.containsAll(listOf("عرض", "نصيحة", "سؤال", "للتجار", "موسم")))
        assertTrue(angles.size >= 8)
        assertEquals(angles.toList(), IdeaBank.angles)
        for ((angle, ideas) in DefaultIdeas.byAngle) assertTrue("$angle has ideas", ideas.size >= 4)
    }

    @Test
    fun localIdeasAreDistinctAndPassTheChecker() {
        var seed = 0
        for (angle in IdeaBank.angles) {
            for (product in TestFixtures.passable) {
                val result = IdeaBank.local(angle, product, brand, Channel.WHATSAPP, null, emptyList(), Random(seed++))
                assertEquals(4, result.ideas.size)
                assertEquals(4, result.ideas.toSet().size)
                for (idea in result.ideas) {
                    val errors = ClaimChecker.check(idea, product, brand, today = "2026-11-12").errors
                    assertEquals("$angle: $idea", emptyList<Any>(), errors)
                    assertTrue(!idea.contains("{"))
                }
            }
        }
    }

    @Test
    fun anIdeaBecomesAFullPostWithALink() {
        val vocab = DefaultVocabulary.tones.getValue("ودّي")
        for (angle in IdeaBank.angles) {
            val idea = IdeaBank.local(angle, TestFixtures.offer, brand, Channel.WHATSAPP, "K9", emptyList(), Random(1)).ideas.first()
            val post = IdeaBank.toPost(idea, angle, vocab, TestFixtures.offer, brand, Channel.WHATSAPP, "K9", Random(2))
            assertTrue("$angle: no link", post.contains("https://wa.me/"))
            assertTrue(post.startsWith(idea))
            assertEquals("$angle: $post", emptyList<Any>(), ClaimChecker.check(post, TestFixtures.offer, brand, today = "2026-11-12").errors)
        }
    }

    @Test
    fun offerAnglesGetAPriceLineAndTheOthersDoNot() {
        val vocab = DefaultVocabulary.tones.getValue("ودّي")
        val offerPost = IdeaBank.toPost("فكرة", "عرض", vocab, TestFixtures.offer, brand, Channel.WHATSAPP, null, Random(4))
        assertTrue(offerPost.lines().size >= 3)
        val tipPost = IdeaBank.toPost("فكرة", "نصيحة", vocab, TestFixtures.offer, brand, Channel.WHATSAPP, null, Random(4))
        assertEquals(2, tipPost.lines().size)
    }

    @Test
    fun theNoLongerRepeatingMemoryAppliesToIdeasToo() {
        val first = IdeaBank.local("عرض", TestFixtures.offer, brand, Channel.WHATSAPP, null, emptyList(), Random(1))
        val second = IdeaBank.local("عرض", TestFixtures.offer, brand, Channel.WHATSAPP, null, first.used, Random(2))
        assertTrue(second.ideas.none { it in first.ideas })
    }
}
