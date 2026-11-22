package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.IncomingStock
import com.cady.cadymarketing.domain.model.InventoryItem
import com.cady.cadymarketing.domain.model.InventoryLinks
import com.cady.cadymarketing.domain.model.InventorySnapshot
import com.cady.cadymarketing.domain.model.Product
import com.cady.cadymarketing.domain.model.SavedContent
import com.cady.cadymarketing.domain.model.StockStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InventoryTest {
    private val day = 86_400_000L
    private val now = 1_795_219_200_000L // 2026-11-21T00:00Z

    private fun item(
        name: String,
        onHand: Long = 100,
        reorderAt: Long = 0,
        tiers: List<Long> = emptyList(),
        achieved: Long = 0,
        barcode: String = "",
        itemNo: String = "",
        incoming: List<IncomingStock> = emptyList(),
        unit: String = "كرتونة",
    ) = InventoryItem(
        key = InventoryRules.itemKey(barcode, itemNo, name), name = name, barcode = barcode, itemNo = itemNo, unit = unit,
        onHand = onHand, reorderAt = reorderAt, goalTiers = tiers, achieved = achieved, incoming = incoming,
    )

    private fun product(id: String, name: String) = Product(id = id, name = name, unit = "كرتونة", price = 1000)

    private fun post(id: String, productId: String, status: ContentStatus, updatedAt: Long = now, productName: String = "صنف") = SavedContent(
        id = id, productId = productId, productName = productName, channel = Channel.WHATSAPP, text = "نص", status = status,
        designId = 0, picturePath = null, scheduledAt = null, campaignId = "k1", campaignCode = null, tone = null, angle = null,
        createdAt = 1L, updatedAt = updatedAt,
    )

    // ---------------------------------------------------------------- names and keys
    @Test
    fun namesAreComparedWithoutHamzaMarksPunctuationOrDigitScript() {
        // Both sides are normalised the same way (hamza on ya becomes ya), so equal names stay equal.
        assertEquals(InventoryRules.normalizeName("سائل جلي ليمون"), InventoryRules.normalizeName("  سائل  جلي - ليمون "))
        assertFalse(InventoryRules.normalizeName("سائل جلي ليمون").contains("  "))
        assertEquals(InventoryRules.normalizeName("أَكياس النفايات (٣٠ لتر)"), InventoryRules.normalizeName("اكياس النفايات 30 لتر"))
        assertEquals(InventoryRules.normalizeName("مُنظِّف ـــ الأرضيّات"), InventoryRules.normalizeName("منظف الارضيات"))
        assertEquals("fresh air", InventoryRules.normalizeName("Fresh_Air"))
        assertEquals(InventoryRules.normalizeName("حلويات"), InventoryRules.normalizeName("حلويات"))
        assertTrue(InventoryRules.normalizeName("سائل جلي") != InventoryRules.normalizeName("سائل جلي ليمون"))
        assertEquals("", InventoryRules.normalizeName(" - _ "))
    }

    @Test
    fun aLineIsIdentifiedByBarcodeThenItemNumberThenName() {
        assertEquals("bc:628100", InventoryRules.itemKey(" 628100 ", "101", "سائل"))
        assertEquals("no:ab-101", InventoryRules.itemKey("", " AB-101 ", "سائل"))
        assertEquals("nm:" + InventoryRules.normalizeName("سائل جلي"), InventoryRules.itemKey("", "", "سائل  جلي"))
    }

    // ---------------------------------------------------------------- how much is left
    @Test
    fun anEmptyShelfIsOutWhateverElseIsKnown() {
        assertEquals(StockStatus.OUT, InventoryRules.status(item("a", onHand = 0, reorderAt = 5, tiers = listOf(100))))
        assertEquals(StockStatus.OUT, InventoryRules.status(item("a", onHand = -3)))
    }

    @Test
    fun lowComesFromTheReorderLevelElseAFifthOfTheFirstGoal() {
        assertEquals(StockStatus.LOW, InventoryRules.status(item("a", onHand = 40, reorderAt = 40)))
        assertEquals(StockStatus.OK, InventoryRules.status(item("a", onHand = 41, reorderAt = 40)))
        assertEquals(StockStatus.LOW, InventoryRules.status(item("a", onHand = 60, tiers = listOf(300, 450, 600))))
        assertEquals(StockStatus.OK, InventoryRules.status(item("a", onHand = 61, tiers = listOf(300, 450, 600))))
        // Nothing to compare with: a line with stock and no goals is simply in stock.
        assertEquals(StockStatus.OK, InventoryRules.status(item("a", onHand = 1)))
    }

    @Test
    fun aSurplusIsTwoFirstGoalsOrMore() {
        assertEquals(StockStatus.OVER, InventoryRules.status(item("a", onHand = 600, tiers = listOf(300, 450, 600))))
        assertEquals(StockStatus.OK, InventoryRules.status(item("a", onHand = 599, tiers = listOf(300, 450, 600))))
        // The reorder level decides "low" first.
        assertEquals(StockStatus.LOW, InventoryRules.status(item("a", onHand = 10, reorderAt = 20, tiers = listOf(5))))
    }

    @Test
    fun theGoalShowsTheNextTierOrTheTopOneWhenAllAreReached() {
        val partway = InventoryRules.goal(item("a", tiers = listOf(600, 300, 450), achieved = 310))!!
        assertEquals(2, partway.tier)
        assertEquals(450L, partway.target)
        assertFalse(partway.reached)
        val done = InventoryRules.goal(item("a", tiers = listOf(300, 450, 600), achieved = 700))!!
        assertTrue(done.reached)
        assertEquals(3, done.tier)
        assertNull(InventoryRules.goal(item("a", tiers = listOf(0, 0))))
        assertTrue(InventoryRules.goalText(partway, "كرتونة").contains("المستوى 2"))
        assertTrue(InventoryRules.goalText(done, "").contains("تحقق"))
    }

    @Test
    fun theNextArrivalIsTheEarliestFromTodayOn() {
        val stock = item("a", incoming = listOf(IncomingStock("2026-11-10", 5), IncomingStock("2026-12-01", 7), IncomingStock("2026-11-28", 9)))
        assertEquals("2026-11-28", InventoryRules.nextArrival(stock, "2026-11-20")!!.date)
        assertEquals("2026-11-28", InventoryRules.nextArrival(stock, "2026-11-28")!!.date)
        assertNull(InventoryRules.nextArrival(stock, "2026-12-02"))
    }

    @Test
    fun promotionHelpsASurplusOrAGoalWithinReachAndNeverARunOutProduct() {
        val over = InventoryRules.info(item("a", onHand = 700, tiers = listOf(300)), "2026-11-20")
        val near = InventoryRules.info(item("b", onHand = 100, tiers = listOf(300), achieved = 250), "2026-11-20")
        val far = InventoryRules.info(item("c", onHand = 100, tiers = listOf(300), achieved = 100), "2026-11-20")
        val out = InventoryRules.info(item("d", onHand = 0, tiers = listOf(300), achieved = 290), "2026-11-20")
        assertTrue(InventoryRules.needsPromotion(over))
        assertTrue(InventoryRules.needsPromotion(near))
        assertFalse(InventoryRules.needsPromotion(far))
        assertTrue(InventoryRules.shouldAvoid(out))
        assertFalse(InventoryRules.shouldAvoid(over))
    }

    @Test
    fun aSnapshotGetsOldAfterAWeek() {
        assertEquals(3, InventoryRules.ageDays(now - 3 * day - 5, now))
        assertEquals(0, InventoryRules.ageDays(now + day, now))
        assertNull(InventoryRules.ageDays(0, now))
        assertFalse(InventoryRules.isStale(7))
        assertTrue(InventoryRules.isStale(8))
        assertFalse(InventoryRules.isStale(null))
    }

    // ---------------------------------------------------------------- what the Studio says
    @Test
    fun theStudioWarnsAboutARunOutProductAndSaysWhenStockComes() {
        val out = InventoryRules.info(item("منظف أرضيات", onHand = 0, incoming = listOf(IncomingStock("2026-11-25", 80)), unit = "جركن"), "2026-11-20")
        val note = StockWarnings.note(out, 2)!!
        assertEquals(NoteLevel.WARNING, note.level)
        assertTrue(note.text.contains("نفد «منظف أرضيات»"))
        assertTrue(note.text.contains("تصل 80 جركن في 2026-11-25"))
        assertFalse(note.text.contains("قبل"))
        assertTrue(StockWarnings.note(out, 9)!!.text.contains("آخر لقطة قبل 9 يومًا"))
    }

    @Test
    fun lowGetsAWarningSurplusAGoodNewsLineAndPlentyNothing() {
        val low = InventoryRules.info(item("a", onHand = 10, reorderAt = 20), "2026-11-20")
        val over = InventoryRules.info(item("b", onHand = 900, tiers = listOf(200)), "2026-11-20")
        val fine = InventoryRules.info(item("c", onHand = 100, tiers = listOf(200)), "2026-11-20")
        assertEquals(NoteLevel.WARNING, StockWarnings.note(low, null)!!.level)
        assertEquals(NoteLevel.INFO, StockWarnings.note(over, null)!!.level)
        assertNull(StockWarnings.note(fine, null))
        assertNull(StockWarnings.note(null, null))
    }

    // ---------------------------------------------------------------- the snapshot file
    private val sample = """
        {"app":"inventory-analyzer","format":1,"createdAt":1795132800000,"branch":"الفرع الرئيسي","items":[
          {"barcode":"6281000100011","itemNo":"101","name":"سائل جلي ليمون","category":"سوائل","unit":"كرتونة",
           "onHand":120,"reorderAt":40,"goalMonth":"2026-11","goalTiers":[300,450,600],"achieved":210,
           "incoming":[{"date":"2026-11-28","qty":200}],"countedAt":1795046400000},
          {"barcode":"","itemNo":"102","name":"منظف أرضيات","category":"سوائل","unit":"جركن",
           "onHand":0,"reorderAt":20,"goalMonth":"2026-11","goalTiers":[100,150,200],"achieved":100,
           "incoming":[{"date":"2026-11-25","qty":80}],"countedAt":1795046400000},
          {"name":"معطر جو","unit":"علبة","onHand":900,"goalMonth":"2026-11","goalTiers":[200,300,400],"achieved":40}
        ]}
    """.trimIndent()

    @Test
    fun theDocumentedSampleFileIsReadInFull() {
        val ok = InventoryJson.decode(sample, now) as InventoryDecode.Ok
        assertEquals(0, ok.skipped)
        assertEquals("الفرع الرئيسي", ok.snapshot.branch)
        assertEquals(1_795_132_800_000L, ok.snapshot.createdAt)
        val items = ok.snapshot.items
        assertEquals(3, items.size)
        assertEquals("bc:6281000100011", items[0].key)
        assertEquals("no:102", items[1].key)
        assertEquals("nm:معطر جو", items[2].key)
        assertEquals(listOf(300L, 450L, 600L), items[0].goalTiers)
        assertEquals(IncomingStock("2026-11-28", 200), items[0].incoming.single())
        assertEquals(StockStatus.OK, InventoryRules.status(items[0]))
        assertEquals(StockStatus.OUT, InventoryRules.status(items[1]))
        assertEquals(StockStatus.OVER, InventoryRules.status(items[2]))
    }

    @Test
    fun usualAlternativeFieldNamesAndNumbersWrittenAsTextAreAccepted() {
        val text = """{"items":[{"productName":"زيت","ean":6281000100028,"quantity":"12.6","reorderLevel":3,
            "goals":["100","200"],"sold":"15","arrivals":[{"date":"2026-12-01T09:00:00","quantity":4},{"date":"soon","qty":1},{"date":"2026-12-02","qty":0}]}]}"""
        val ok = InventoryJson.decode(text, now) as InventoryDecode.Ok
        val line = ok.snapshot.items.single()
        assertEquals("زيت", line.name)
        assertEquals("6281000100028", line.barcode)
        assertEquals(13L, line.onHand)
        assertEquals(3L, line.reorderAt)
        assertEquals(listOf(100L, 200L), line.goalTiers)
        assertEquals(15L, line.achieved)
        assertEquals(listOf(IncomingStock("2026-12-01", 4)), line.incoming)
        assertEquals(now, ok.snapshot.createdAt)
    }

    @Test
    fun linesWithoutANameAndRepeatedLinesAreSkippedAndCounted() {
        val text = """{"items":[{"name":"أ","barcode":"1"},{"name":"   "},{"barcode":"2"},{"name":"ب","barcode":"1"},{"name":"ج"}]}"""
        val ok = InventoryJson.decode(text, now) as InventoryDecode.Ok
        assertEquals(listOf("أ", "ج"), ok.snapshot.items.map { it.name })
        assertEquals(3, ok.skipped)
    }

    @Test
    fun filesThatAreNotAUsableSnapshotAreRefusedWithAReason() {
        assertTrue(InventoryJson.decode("not json", now) is InventoryDecode.Bad)
        assertTrue(InventoryJson.decode("[1]", now) is InventoryDecode.Bad)
        assertTrue(InventoryJson.decode("{\"app\":\"cady-marketing\",\"items\":[{\"name\":\"x\"}]}", now) is InventoryDecode.Bad)
        val newer = InventoryJson.decode("{\"format\":9,\"items\":[{\"name\":\"x\"}]}", now)
        assertTrue((newer as InventoryDecode.Bad).message.contains("حدّث"))
        assertTrue(InventoryJson.decode("{\"app\":\"inventory-analyzer\"}", now) is InventoryDecode.Bad)
        assertTrue(InventoryJson.decode("{\"items\":[]}", now) is InventoryDecode.Bad)
        assertTrue(InventoryJson.decode("{\"items\":[{\"name\":\"\"}]}", now) is InventoryDecode.Bad)
    }

    @Test
    fun theStoredFormRoundTripsAndABrokenStoredDocumentIsNull() {
        val ok = InventoryJson.decode(sample, now) as InventoryDecode.Ok
        val stored = InventoryJson.encode(ok.snapshot)
        assertEquals(ok.snapshot, InventoryJson.decodeStored(stored))
        assertNull(InventoryJson.decodeStored(null))
        assertNull(InventoryJson.decodeStored(""))
        assertNull(InventoryJson.decodeStored("{"))
    }

    @Test
    fun linksRoundTripAndBadEntriesAreIgnored() {
        val links = InventoryLinks(linkedMapOf("bc:1" to "p1", "nm:زيت" to "p2"))
        assertEquals(links, InventoryJson.decodeLinks(InventoryJson.encodeLinks(links)))
        assertEquals(InventoryLinks(), InventoryJson.decodeLinks(null))
        assertEquals(InventoryLinks(mapOf("a" to "b")), InventoryJson.decodeLinks("{\"links\":[{\"key\":\"a\",\"productId\":\"b\"},{\"key\":\"\",\"productId\":\"x\"},{\"key\":\"c\"},3]}"))
    }

    // ---------------------------------------------------------------- linking
    @Test
    fun onlyAnUnambiguousExactNameIsSuggested() {
        val items = listOf(item("سائل جلي ليمون", barcode = "1"), item("منظّف أرضيات", barcode = "2"), item("معطر", barcode = "3"))
        val products = listOf(product("p1", "سائل جلي ليمون"), product("p2", "منظف الأرضيات"), product("p3", "معطر جو"))
        val suggestions = InventoryLinking.suggestions(items, products, InventoryLinks())
        assertEquals(mapOf("bc:1" to "p1"), suggestions)
        // "منظّف أرضيات" and "منظف الأرضيات" are different names; "معطر" and "معطر جو" are different too: no similarity matching.
    }

    @Test
    fun twoLinesOrTwoProductsWithTheSameNameSuggestNothing() {
        val twoLines = listOf(item("زيت", barcode = "1"), item("زيت", barcode = "2"))
        assertTrue(InventoryLinking.suggestions(twoLines, listOf(product("p1", "زيت")), InventoryLinks()).isEmpty())
        val twoProducts = listOf(item("زيت", barcode = "1"))
        assertTrue(InventoryLinking.suggestions(twoProducts, listOf(product("p1", "زيت"), product("p2", "زيت")), InventoryLinks()).isEmpty())
    }

    @Test
    fun aProductAlreadyLinkedIsNotSuggestedAgain() {
        val items = listOf(item("زيت", barcode = "1"), item("زيت", barcode = "2", unit = "علبة"))
        val links = InventoryLinks(mapOf("bc:2" to "p1"))
        assertTrue(InventoryLinking.suggestions(items, listOf(product("p1", "زيت")), links).isEmpty())
    }

    @Test
    fun theLinkStatesTellLinkedSuggestedAndLostLinksApart() {
        val snapshot = InventorySnapshot(now, "", listOf(item("أ", barcode = "1"), item("ب", barcode = "2"), item("ج", barcode = "3")))
        val products = listOf(product("p1", "أ"), product("p2", "ب"))
        val links = InventoryLinks(mapOf("bc:2" to "p2", "bc:3" to "gone"))
        val states = InventoryLinking.states(snapshot, links, products)
        assertNull(states[0].productId)
        assertEquals("p1", states[0].suggestedProductId)
        assertEquals("p2", states[1].productId)
        assertNull(states[1].suggestedProductId)
        // A link to a product Kady no longer has is not used.
        assertNull(states[2].productId)
    }

    @Test
    fun theStockOfAProductComesOnlyFromItsConfirmedLine() {
        val snapshot = InventorySnapshot(now, "", listOf(item("أ", onHand = 0, barcode = "1"), item("ب", onHand = 50, barcode = "2")))
        val byProduct = InventoryLinking.byProduct(snapshot, InventoryLinks(mapOf("bc:1" to "p1", "bc:9" to "p9")), "2026-11-20")
        assertEquals(setOf("p1"), byProduct.keys)
        assertEquals(StockStatus.OUT, byProduct.getValue("p1").status)
    }

    @Test
    fun linkingAProductAgainMovesItAndUnlinkingRemovesIt() {
        val first = InventoryLinking.withLink(InventoryLinks(), "bc:1", "p1")
        val moved = InventoryLinking.withLink(first, "bc:2", "p1")
        assertEquals(mapOf("bc:2" to "p1"), moved.byKey)
        assertTrue(InventoryLinking.withoutLink(moved, "bc:2").byKey.isEmpty())
        val many = InventoryLinking.withLinks(InventoryLinks(), mapOf("bc:1" to "p1", "bc:2" to "p2"))
        assertEquals(2, many.byKey.size)
    }

    // ---------------------------------------------------------------- the home alerts, the calendar and the campaign
    @Test
    fun aRunOutProductWithAReadyPostIsUrgent() {
        val products = listOf(product("p1", "منظف"), product("p2", "جلي"))
        val stock = mapOf(
            "p1" to InventoryRules.info(item("منظف", onHand = 0, incoming = listOf(IncomingStock("2026-11-25", 80))), "2026-11-20"),
            "p2" to InventoryRules.info(item("جلي", onHand = 0), "2026-11-20"),
        )
        val posts = listOf(post("a", "p1", ContentStatus.APPROVED), post("b", "p2", ContentStatus.DRAFT))
        val alerts = InventoryInsights.alerts(stock, products, posts, now, 1)
        assertEquals(1, alerts.size)
        assertEquals(AlertLevel.URGENT, alerts[0].level)
        assertTrue(alerts[0].title.contains("منظف"))
        assertTrue(alerts[0].detail.contains("2026-11-25"))
        assertEquals(AlertTarget.LIBRARY, alerts[0].target)
    }

    @Test
    fun aPostPublishedLongAgoDoesNotMakeARunOutProductUrgent() {
        val products = listOf(product("p1", "منظف"))
        val stock = mapOf("p1" to InventoryRules.info(item("منظف", onHand = 0), "2026-11-20"))
        val old = listOf(post("a", "p1", ContentStatus.PUBLISHED, updatedAt = now - 30 * day))
        val fresh = listOf(post("a", "p1", ContentStatus.PUBLISHED, updatedAt = now - 2 * day))
        assertTrue(InventoryInsights.alerts(stock, products, old, now, 1).isEmpty())
        assertEquals(1, InventoryInsights.alerts(stock, products, fresh, now, 1).size)
    }

    @Test
    fun surplusWithoutAPostLatelyIsOneAlertOpeningTheStudioForASingleProduct() {
        val products = listOf(product("p1", "جلي"), product("p2", "معطر"), product("p3", "صابون"))
        val over = { name: String -> InventoryRules.info(item(name, onHand = 900, tiers = listOf(200)), "2026-11-20") }
        val one = InventoryInsights.alerts(mapOf("p1" to over("جلي")), products, emptyList(), now, 1)
        assertEquals(AlertTarget.STUDIO, one.single().target)
        assertEquals("p1", one.single().productId)
        val several = InventoryInsights.alerts(mapOf("p1" to over("جلي"), "p2" to over("معطر")), products, emptyList(), now, 1)
        assertEquals(AlertTarget.PRODUCTS, several.single().target)
        assertTrue(several.single().title.contains("2"))
        // A recent post for the product removes it from the surplus alert.
        val posted = InventoryInsights.alerts(mapOf("p1" to over("جلي")), products, listOf(post("a", "p1", ContentStatus.DRAFT, updatedAt = now - 3 * day)), now, 1)
        assertTrue(posted.isEmpty())
    }

    @Test
    fun anOldSnapshotIsAnInfoAlertAndNoSnapshotMeansNoAlerts() {
        val alerts = InventoryInsights.alerts(emptyMap(), emptyList(), emptyList(), now, 12)
        assertEquals(AlertLevel.INFO, alerts.single().level)
        assertTrue(alerts.single().title.contains("12"))
        assertTrue(InventoryInsights.alerts(emptyMap(), emptyList(), emptyList(), now, null).isEmpty())
    }

    @Test
    fun deliveriesAreGroupedByDayAndNamedAfterTheLinkedProduct() {
        val snapshot = InventorySnapshot(
            now, "",
            listOf(
                item("سائل جلي (مخزن)", barcode = "1", incoming = listOf(IncomingStock("2026-11-28", 200), IncomingStock("2026-12-20", 5))),
                item("معطر", barcode = "2", incoming = listOf(IncomingStock("2026-11-28", 30))),
            ),
        )
        val links = InventoryLinks(mapOf("bc:1" to "p1"))
        val arrivals = InventoryInsights.arrivals(snapshot, links, listOf(product("p1", "سائل جلي")), "2026-11-01", "2026-12-06")
        assertEquals(setOf("2026-11-28"), arrivals.keys)
        val rows = arrivals.getValue("2026-11-28")
        assertEquals(listOf("سائل جلي", "معطر"), rows.map { it.itemName })
        assertEquals("p1", rows[0].productId)
        assertNull(rows[1].productId)
        assertTrue(InventoryInsights.arrivals(null, links, emptyList(), "2026-11-01", "2026-12-06").isEmpty())
    }

    @Test
    fun theCampaignPageListsItsProductsWithTheOnesThatRanOutFirst() {
        val stock = mapOf(
            "p1" to InventoryRules.info(item("أ", onHand = 500, tiers = listOf(100)), "2026-11-20"),
            "p2" to InventoryRules.info(item("ب", onHand = 0), "2026-11-20"),
        )
        val posts = listOf(
            post("1", "p1", ContentStatus.DRAFT, productName = "أ"),
            post("2", "p1", ContentStatus.APPROVED, productName = "أ"),
            post("3", "p2", ContentStatus.DRAFT, productName = "ب"),
            post("4", "p3", ContentStatus.DRAFT, productName = "ج"),
            post("5", "p4", ContentStatus.ARCHIVED, productName = "د"),
        )
        val rows = InventoryInsights.campaignRows(posts, stock, true)
        assertEquals(listOf("p2", "p1", "p3"), rows.map { it.productId })
        assertNull(rows[2].info)
        assertTrue(InventoryInsights.campaignRows(posts, stock, false).isEmpty())
    }

    // ---------------------------------------------------------------- the idea of the day
    @Test
    fun theIdeaOfTheDayLeansTowardASurplusProductButOtherwiseStaysAsItWas() {
        val products = (1..6).map { product("p$it", "صنف $it") }
        val bank = DefaultIdeas.byAngle
        val plain = (0 until 12).map { DailyIdeas.pick(CalendarMath.addDays("2026-11-16", it), products, bank, Brand()) }
        val same = (0 until 12).map { DailyIdeas.pick(CalendarMath.addDays("2026-11-16", it), products, bank, Brand(), emptySet()) }
        assertEquals(plain, same)
        val unknown = (0 until 12).map { DailyIdeas.pick(CalendarMath.addDays("2026-11-16", it), products, bank, Brand(), setOf("nope")) }
        assertEquals(plain, unknown)
        val leaning = (0 until 12).map { DailyIdeas.pick(CalendarMath.addDays("2026-11-16", it), products, bank, Brand(), setOf("p5")) }
        assertTrue(leaning.all { it != null && it.product.id == "p5" })
        assertNotNull(leaning.first())
    }

    @Test
    fun preferredProductsAreThoseInSurplus() {
        val stock = mapOf(
            "p1" to InventoryRules.info(item("أ", onHand = 900, tiers = listOf(100)), "2026-11-20"),
            "p2" to InventoryRules.info(item("ب", onHand = 0), "2026-11-20"),
        )
        assertEquals(setOf("p1"), InventoryRules.preferredForIdeas(stock))
    }
}
