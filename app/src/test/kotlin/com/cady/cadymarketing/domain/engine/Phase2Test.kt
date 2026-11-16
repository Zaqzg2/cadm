package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Brand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase2Test {
    // ---------------------------------------------------------------- json writer
    @Test
    fun jsonIsWrittenAndReadBack() {
        val value = linkedMapOf(
            "s" to "سطر \"أول\"\n\tثان \\",
            "n" to 7000L,
            "d" to 2.5,
            "w" to 3.0,
            "b" to true,
            "z" to null,
            "l" to listOf(1, "x", listOf<Any?>(), emptyMap<String, Any?>()),
        )
        val text = MiniJson.write(value)
        val back = MiniJson.parse(text) as Map<*, *>
        assertEquals("سطر \"أول\"\n\tثان \\", back["s"])
        assertEquals(7000.0, back["n"])
        assertEquals(2.5, back["d"])
        assertEquals(3.0, back["w"])
        assertEquals(true, back["b"])
        assertNull(back["z"])
        assertEquals(listOf(1.0, "x", emptyList<Any?>(), emptyMap<String, Any?>()), back["l"])
        assertTrue(text.contains("\"w\":3,"))
    }

    // ---------------------------------------------------------------- brand
    @Test
    fun hexColoursAreNormalised() {
        assertEquals("#c8102e", BrandRules.normalizeHex(" #C8102E "))
        assertEquals("#aabbcc", BrandRules.normalizeHex("abc"))
        assertNull(BrandRules.normalizeHex("#12345"))
        assertNull(BrandRules.normalizeHex("red"))
        assertEquals(0xFFC8102E.toInt(), BrandRules.argb("#c8102e"))
        assertNull(BrandRules.argb("zz"))
    }

    @Test
    fun aDarkShadeIsDerivedFromTheColour() {
        assertEquals("#38040c", BrandRules.darken("#c8102e"))
        assertEquals("#000000", BrandRules.darken("#000"))
        assertNull(BrandRules.darken("blue"))
    }

    @Test
    fun hashtagsAreCleaned() {
        assertEquals(listOf("#كادي", "#منظفات", "#اليمن"), BrandRules.parseHashtags("كادي  #منظفات, اليمن،#كادي"))
        assertEquals(emptyList<String>(), BrandRules.parseHashtags(" # , "))
    }

    @Test
    fun brandValidationNamesEachProblem() {
        assertEquals(emptyList<String>(), BrandRules.validate(Brand()))
        val bad = Brand(name = " ", primaryHex = "x", whatsapp = "12", siteUrl = "cadyco")
        assertEquals(4, BrandRules.validate(bad).size)
        assertTrue(BrandRules.validate(Brand(whatsapp = "+967 730 487 946")).isEmpty())
    }

    @Test
    fun brandRoundTripsAndOldDocumentsStillRead() {
        val brand = Brand(name = "كادي 2", primaryHex = "#112233", hashtags = listOf("#a"), logoFile = "x.png")
        assertEquals(brand, BrandJson.decode(BrandJson.encode(brand)))
        assertEquals(Brand(), BrandJson.decode(null))
        assertEquals(Brand(), BrandJson.decode("{broken"))
        assertEquals(Brand(), BrandJson.decode("{}"))
        assertEquals(Brand(name = "س"), BrandJson.decode("{\"name\":\"س\"}"))
        assertEquals(emptyList<String>(), BrandJson.decode("{\"hashtags\":[]}").hashtags)
    }

    @Test
    fun cleanBrandIsReadyToSave() {
        val cleaned = BrandRules.clean(
            Brand(name = " كادي ", primaryHex = "C8102E", whatsapp = "٧٣٠ ٤٨٧ ٩٤٦", bannedPhrases = " يقتل , علاج ", hashtags = listOf("a b", "#c")),
        )
        assertEquals("كادي", cleaned.name)
        assertEquals("#c8102e", cleaned.primaryHex)
        assertEquals("730487946", cleaned.whatsapp)
        assertEquals("يقتل،علاج", cleaned.bannedPhrases)
        assertEquals(listOf("#a", "#b", "#c"), cleaned.hashtags)
    }

    // ---------------------------------------------------------------- vocabulary
    @Test
    fun noEditsMeansTheDefaults() {
        val merged = VocabularyMerge.merge(VocabularyEdits())
        assertEquals(DefaultVocabulary.tones, merged.tones)
        assertEquals(DefaultIdeas.byAngle, merged.ideas)
        assertEquals(DefaultVocabulary.templates, merged.templates)
        assertEquals(DefaultIdeas.withPriceLine, merged.priceAngles)
    }

    @Test
    fun linesCanBeAddedHiddenAndRestored() {
        val key = VocabKey.tone("ودّي", VocabCategory.HOOKS)
        val builtIn = DefaultVocabulary.tones.getValue("ودّي").hooks
        var edits = VocabularyEdits().addLine(key, "  أهلًا بكم في {العلامة} ").addLine(key, "أهلًا بكم في {العلامة}")
        assertEquals(builtIn + "أهلًا بكم في {العلامة}", VocabularyMerge.merge(edits).tones.getValue("ودّي").hooks)

        edits = edits.removeLine(key, builtIn.first(), builtIn = true)
        assertFalse(builtIn.first() in VocabularyMerge.merge(edits).tones.getValue("ودّي").hooks)
        edits = edits.addLine(key, builtIn.first())
        assertTrue(builtIn.first() in VocabularyMerge.merge(edits).tones.getValue("ودّي").hooks)
        assertEquals(listOf("أهلًا بكم في {العلامة}"), edits.lineEdits(key).added)

        edits = edits.removeLine(key, "أهلًا بكم في {العلامة}", builtIn = false)
        assertTrue(edits.isUntouched)
    }

    @Test
    fun aRequiredListNeverEmptiesOut() {
        val key = VocabKey.tone("رسمي", VocabCategory.CALLS)
        var edits = VocabularyEdits()
        for (line in DefaultVocabulary.tones.getValue("رسمي").calls) edits = edits.removeLine(key, line, builtIn = true)
        val calls = VocabularyMerge.merge(edits).tones.getValue("رسمي").calls
        assertEquals(DefaultVocabulary.tones.getValue("رسمي").calls, calls)
    }

    @Test
    fun aNewToneStartsAsACopyAndCanBeRemoved() {
        val source = DefaultVocabulary.tones.getValue("حماسي")
        val edits = VocabularyEdits().addTone(" نبرة العيد ", source)
        val merged = VocabularyMerge.merge(edits)
        assertEquals(source, merged.tones.getValue("نبرة العيد"))
        assertEquals(5, merged.tones.size)
        val gone = edits.removeCustomTone("نبرة العيد")
        assertTrue(gone.isUntouched)
        assertEquals(4, VocabularyMerge.merge(gone).tones.size)
    }

    @Test
    fun hidingEveryToneBringsTheDefaultsBack() {
        var edits = VocabularyEdits()
        for (name in DefaultVocabulary.tones.keys) edits = edits.hideTone(name)
        assertEquals(DefaultVocabulary.tones.keys, VocabularyMerge.merge(edits).tones.keys)
        assertEquals(setOf("ودّي", "رسمي", "حماسي"), VocabularyMerge.merge(VocabularyEdits().hideTone("تجاري")).tones.keys)
    }

    @Test
    fun ideasAndAnglesAreEditable() {
        var edits = VocabularyEdits().addAngle("رمضان", listOf("رمضان كريم من {العلامة}", "بيتك جاهز مع {الاسم}"))
        var merged = VocabularyMerge.merge(edits)
        assertEquals(listOf("رمضان كريم من {العلامة}", "بيتك جاهز مع {الاسم}"), merged.ideas.getValue("رمضان"))
        assertFalse("رمضان" in merged.priceAngles)
        edits = edits.hideAngle("عرض")
        merged = VocabularyMerge.merge(edits)
        assertFalse("عرض" in merged.ideas)
        assertFalse("عرض" in merged.priceAngles)
        edits = edits.addLine(VocabKey.idea("نصيحة"), "فكرة جديدة {الاسم}")
        assertTrue("فكرة جديدة {الاسم}" in VocabularyMerge.merge(edits).ideas.getValue("نصيحة"))
    }

    @Test
    fun templatesCanBeAddedEditedHiddenAndRemoved() {
        var edits = VocabularyEdits().addTemplate(TextTemplate("c1", "عرض اليوم", "{الاسم} بـ {السعر} ريال\n{الرابط}"))
        assertEquals(listOf("t1", "c1"), VocabularyMerge.merge(edits).templates.map { it.id })
        edits = edits.updateTemplate(TextTemplate("c1", "عرض الأسبوع", "{الاسم}\n{الرابط}"))
        assertEquals("عرض الأسبوع", VocabularyMerge.merge(edits).templates.last().name)
        edits = edits.hideBuiltInTemplate("t1")
        assertEquals(listOf("c1"), VocabularyMerge.merge(edits).templates.map { it.id })
        edits = edits.restoreBuiltInTemplate("t1").removeTemplate("c1")
        assertTrue(edits.isUntouched)
    }

    @Test
    fun lintRulesMatchTheBuiltInVocabulary() {
        val banned = Brand().bannedList
        for ((tone, v) in DefaultVocabulary.tones) {
            for (category in VocabCategory.entries) {
                for (line in category.linesOf(v)) {
                    assertEquals("$tone/${category.key}: $line", emptyList<String>(), VocabularyLint.problems(line, category, banned))
                }
            }
        }
        for (template in DefaultVocabulary.templates) {
            assertEquals(emptyList<String>(), VocabularyLint.problems(template.text, null, banned, allowCustomerName = true))
        }
    }

    @Test
    fun lintCatchesTheProblems() {
        val banned = Brand().bannedList
        assertEquals(listOf("السطر فارغ"), VocabularyLint.problems("  ", VocabCategory.HOOKS, banned))
        assertTrue(VocabularyLint.problems("خصم 50 ريال", VocabCategory.HOOKS, banned).any { it.contains("أرقامًا") })
        assertTrue(VocabularyLint.problems("خصم ٥٠", VocabCategory.HOOKS, banned).any { it.contains("أرقامًا") })
        assertTrue(VocabularyLint.problems("ينظف ويقتل الجراثيم", VocabCategory.HOOKS, banned).any { it.contains("ممنوعة") })
        assertTrue(VocabularyLint.problems("مرحبًا {الاسمم}", VocabCategory.HOOKS, banned).any { it.contains("غير معروف") })
        assertTrue(VocabularyLint.problems("عرض خاص", VocabCategory.OFFERS, banned).any { it.contains("{السعر}") })
        assertTrue(VocabularyLint.problems("اطلب الحين", VocabCategory.CALLS, banned).any { it.contains("{الرابط}") })
        assertTrue(VocabularyLint.problems("ينتهي قريبًا", VocabCategory.URGENCY, banned).any { it.contains("{الانتهاء}") })
        assertTrue(VocabularyLint.problems("أهلًا {اسم العميل}", VocabCategory.HOOKS, banned).isNotEmpty())
        assertTrue(VocabularyLint.problems("أهلًا {اسم العميل}", null, banned, allowCustomerName = true).isEmpty())
    }

    @Test
    fun editsRoundTripThroughJson() {
        val edits = VocabularyEdits()
            .addLine(VocabKey.tone("ودّي", VocabCategory.HOOKS), "سطر {الاسم}")
            .removeLine(VocabKey.tone("ودّي", VocabCategory.CALLS), DefaultVocabulary.tones.getValue("ودّي").calls.first(), true)
            .addTone("خاصة", DefaultVocabulary.tones.getValue("رسمي"))
            .hideTone("تجاري")
            .addAngle("موسم الشتاء", listOf("برد {الاسم}"))
            .hideAngle("سؤال")
            .addTemplate(TextTemplate("c9", "قالب", "{الاسم}\n{الرابط}"))
            .hideBuiltInTemplate("t1")
            .copy(updatedAt = 1234L)
        assertEquals(edits, VocabularyJson.decode(VocabularyJson.encode(edits)))
        assertEquals(VocabularyEdits(), VocabularyJson.decode(null))
        assertEquals(VocabularyEdits(), VocabularyJson.decode("not json"))
    }

    // ---------------------------------------------------------------- kady documents
    @Test
    fun productsAreReadFromTheLikelyFieldNames() {
        val a = KadyMapper.product("p1", mapOf("name" to " سائل جلي ", "unit" to "كرتونة", "price" to 7000.0, "isActive" to true))
        assertEquals(KadyProduct("p1", "سائل جلي", "كرتونة", 7000L, true), (a as Mapped.Ok).value)
        val b = KadyMapper.product("p2", mapOf("title" to "منظف", "salePrice" to "7,000", "active" to false))
        assertEquals(KadyProduct("p2", "منظف", "", 7000L, false), (b as Mapped.Ok).value)
        val c = KadyMapper.product("p3", mapOf("name" to "س", "price" to 1L, "deleted" to true))
        assertFalse((c as Mapped.Ok).value.active)
        assertTrue(KadyMapper.product("p4", mapOf("price" to 5)) is Mapped.Skipped)
        assertTrue(KadyMapper.product("p5", mapOf("name" to "س")) is Mapped.Skipped)
    }

    @Test
    fun datesAreReadFromEveryShape() {
        assertEquals("2026-11-30", KadyMapper.date("2026-11-30"))
        assertEquals("2026-11-30", KadyMapper.date("2026-11-30T23:59:00Z"))
        assertEquals("2026-03-05", KadyMapper.date("5/3/2026"))
        assertEquals("2026-03-05", KadyMapper.date("٢٠٢٦-٠٣-٠٥"))
        assertEquals(DateUtil.fromMillis(1_790_000_000_000L), KadyMapper.date(1_790_000_000_000L))
        assertEquals(DateUtil.fromMillis(1_790_000_000_000L), KadyMapper.date(1_790_000_000L))
        assertNull(KadyMapper.date("غدًا"))
        assertNull(KadyMapper.date(null))
        assertNull(KadyMapper.date(0L))
    }

    @Test
    fun offersAreReadAsPercentPriceOrAmount() {
        val percent = KadyMapper.offer("o1", mapOf("productId" to "p1", "percent" to 10, "startDate" to "2026-11-01", "endDate" to "2026-11-30"))
        val offer = (percent as Mapped.Ok).value
        assertEquals(10.0, offer.percent)
        assertEquals("2026-11-30", offer.endDate)
        val price = (KadyMapper.offer("o2", mapOf("product" to "p1", "offerPrice" to "6,800")) as Mapped.Ok).value
        assertEquals(6800L, price.offerPrice)
        val amount = (KadyMapper.offer("o3", mapOf("productId" to "p1", "discountAmount" to 200, "isActive" to false)) as Mapped.Ok).value
        assertEquals(200L, amount.amount)
        assertFalse(amount.active)
        assertTrue(KadyMapper.offer("o4", mapOf("percent" to 5)) is Mapped.Skipped)
        assertTrue(KadyMapper.offer("o5", mapOf("productId" to "p1")) is Mapped.Skipped)
    }

    private val product = KadyProduct("p1", "سائل", "كرتونة", 7000L, true)
    private fun offer(id: String, percent: Double? = null, price: Long? = null, amount: Long? = null, start: String? = null, end: String? = null, active: Boolean = true) =
        KadyOffer(id, "p1", percent, price, amount, start, end, active)

    @Test
    fun offerPricesAreComputedAndGuarded() {
        assertEquals(6300L, OfferResolver.offerPriceOf(7000L, offer("a", percent = 10.0)))
        assertEquals(6800L, OfferResolver.offerPriceOf(7000L, offer("b", price = 6800L)))
        assertEquals(6800L, OfferResolver.offerPriceOf(7000L, offer("c", amount = 200L)))
        assertNull(OfferResolver.offerPriceOf(7000L, offer("d", price = 7000L)))
        assertNull(OfferResolver.offerPriceOf(7000L, offer("e", price = 9000L)))
        assertNull(OfferResolver.offerPriceOf(7000L, offer("f", percent = 0.0)))
        assertNull(OfferResolver.offerPriceOf(7000L, offer("g", percent = 100.0)))
        assertNull(OfferResolver.offerPriceOf(7000L, offer("h", amount = 7000L)))
        assertEquals(6667L, OfferResolver.offerPriceOf(7000L, offer("i", percent = 4.76)))
    }

    @Test
    fun onlyARunningOfferIsApplied() {
        val today = "2026-11-15"
        assertTrue(OfferResolver.isRunning(offer("a", percent = 5.0), today))
        assertTrue(OfferResolver.isRunning(offer("a", percent = 5.0, start = today, end = today), today))
        assertFalse(OfferResolver.isRunning(offer("a", percent = 5.0, start = "2026-11-16"), today))
        assertFalse(OfferResolver.isRunning(offer("a", percent = 5.0, end = "2026-11-14"), today))
        assertFalse(OfferResolver.isRunning(offer("a", percent = 5.0, active = false), today))
    }

    @Test
    fun theBestRunningOfferWins() {
        val today = "2026-11-15"
        val offers = listOf(
            offer("a", percent = 5.0, end = "2026-12-01"),
            offer("b", percent = 10.0, end = "2026-11-20"),
            offer("c", percent = 50.0, end = "2026-11-01"),
            KadyOffer("z", "other", 90.0, null, null, null, null, true),
        )
        assertEquals(6300L to "2026-11-20", OfferResolver.best(product, offers, today))
        val marketing = OfferResolver.toProduct(product, offers, today, listOf("ميزة"), "a.jpg")
        assertEquals(6300L, marketing.offerPrice)
        assertEquals("2026-11-20", marketing.offerEnd)
        assertEquals(listOf("ميزة"), marketing.features)
        assertEquals("a.jpg", marketing.picturePath)
        assertTrue(marketing.hasOffer)
        val none = OfferResolver.toProduct(product, emptyList(), today, emptyList(), null)
        assertFalse(none.hasOffer)
        assertNotNull(none)
    }
}
