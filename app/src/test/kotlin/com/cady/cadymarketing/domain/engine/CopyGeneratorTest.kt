package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.Product
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CopyGeneratorTest {
    private val brand = TestFixtures.brand
    private val today = "2026-11-12"

    private fun request(product: Product, channel: Channel, tone: String, used: List<String> = emptyList()) =
        GenerationRequest(product, brand, channel, tone, campaignCode = "K4F2A", used = used)

    @Test
    fun everyGeneratedFormulaPassesTheChecker() {
        var seed = 0
        for (tone in DefaultVocabulary.tones.keys) {
            for (channel in Channel.entries) {
                for (product in TestFixtures.passable) {
                    val result = CopyGenerator.generate(request(product, channel, tone), Random(seed++))
                    for (formula in result.formulas) {
                        val errors = ClaimChecker.check(formula.text, product, brand, channel, today).errors
                        assertEquals("$tone/${channel.code}/${product.id}/${formula.name}: ${formula.text}", emptyList<Any>(), errors)
                    }
                }
            }
        }
    }

    @Test
    fun fiveDistinctFormulasWithLinksAndNoLeftoverPlaceholders() {
        var seed = 100
        for (tone in DefaultVocabulary.tones.keys) {
            for (channel in Channel.entries) {
                for (product in TestFixtures.passable) {
                    val formulas = CopyGenerator.generate(request(product, channel, tone), Random(seed++)).formulas
                    assertEquals(5, formulas.size)
                    assertEquals("$tone/${channel.code}/${product.id}", 5, formulas.map { it.text }.toSet().size)
                    for (formula in formulas) {
                        assertTrue("no link: ${formula.text}", formula.text.contains("http"))
                        assertFalse("placeholder left: ${formula.text}", formula.text.contains("{"))
                        assertTrue(formula.name.isNotBlank())
                    }
                }
            }
        }
    }

    @Test
    fun hashtagsOnlyOnInstagramAndTikTok() {
        val tag = brand.hashtags.first()
        for (channel in Channel.entries) {
            val formulas = CopyGenerator.generate(request(TestFixtures.offer, channel, "ودّي"), Random(7)).formulas
            val expected = channel == Channel.INSTAGRAM || channel == Channel.TIKTOK
            assertTrue(channel.code, formulas.all { it.text.contains(tag) == expected })
        }
    }

    @Test
    fun theOfferEndDateAppearsWhenThereIsOne() {
        val formulas = CopyGenerator.generate(request(TestFixtures.offerWithEnd, Channel.WHATSAPP, "ودّي"), Random(3)).formulas
        assertTrue(formulas.any { it.text.contains("2099-12-31") })
        val without = CopyGenerator.generate(request(TestFixtures.offer, Channel.WHATSAPP, "ودّي"), Random(3)).formulas
        assertTrue(without.none { it.text.contains("2099") })
    }

    @Test
    fun theMemoryMakesTheNextRoundDifferent() {
        val first = CopyGenerator.generate(request(TestFixtures.offer, Channel.WHATSAPP, "ودّي"), Random(1))
        val second = CopyGenerator.generate(request(TestFixtures.offer, Channel.WHATSAPP, "ودّي", first.used), Random(2))
        val firstTexts = first.formulas.map { it.text }.toSet()
        assertTrue(second.formulas.none { it.text in firstTexts })
        assertTrue(second.used.size <= PhraseMemory.CAPACITY)
        assertTrue(second.used.containsAll(first.used.takeLast(10)))
    }

    @Test
    fun theMemoryIsCappedAndKeepsTheNewest() {
        val old = (1..200).map { "قديم $it" }
        val remembered = PhraseMemory.remember(old, listOf("جديد"))
        assertEquals(PhraseMemory.CAPACITY, remembered.size)
        assertEquals("جديد", remembered.last())
    }

    @Test
    fun freshPutsNeverUsedPhrasesFirstAndTheOldestUsedBeforeTheNewest() {
        val candidates = listOf("a", "b", "c", "d")
        val used = listOf("c", "a", "d")   // b never used; c oldest, then a, then d newest
        val ordered = PhraseMemory.fresh(candidates, used, Random(5))
        assertEquals(listOf("b", "c", "a", "d"), ordered)
    }

    @Test
    fun directFormulaKeepsTheFactsInAFixedLayout() {
        val formula = CopyGenerator.direct(TestFixtures.offerWithEnd, brand, Channel.FACEBOOK, "K1")
        assertEquals("مباشر", formula.name)
        assertTrue(formula.text.startsWith("كادي | سائل جلي كادي — ليمون\n"))
        assertTrue(formula.text.contains("السعر: 6,800 ريال بدل 7,000"))
        assertTrue(formula.text.contains("ينتهي العرض: 2099-12-31"))
        assertTrue(formula.text.contains("للطلب: https://cadyco.web.app/?c=K1&ch=fb"))
    }

    @Test
    fun anUnknownToneFallsBackToTheFirstOne() {
        val result = CopyGenerator.generate(request(TestFixtures.offer, Channel.WHATSAPP, "نبرة غير موجودة"), Random(9))
        assertEquals(5, result.formulas.size)
        assertNotEquals("", result.formulas.first().text)
    }

    @Test
    fun anUnsafeFeatureIsNeverUsed() {
        val unsafe = TestFixtures.plain.copy(features = listOf("يقتل الجراثيم", "خلطة بـ 99 مكوّنًا"))
        for (seed in 0 until 20) {
            val formulas = CopyGenerator.generate(request(unsafe, Channel.FACEBOOK, "ودّي"), Random(seed)).formulas
            assertTrue(formulas.none { it.text.contains("يقتل") || it.text.contains("99") })
        }
    }
}
