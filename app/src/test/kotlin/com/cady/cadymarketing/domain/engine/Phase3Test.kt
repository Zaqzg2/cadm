package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Campaign
import com.cady.cadymarketing.domain.model.CampaignPhase
import com.cady.cadymarketing.domain.model.CampaignStatus
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.Occasion
import com.cady.cadymarketing.domain.model.Product
import com.cady.cadymarketing.domain.model.ProductRow
import com.cady.cadymarketing.domain.model.SavedContent
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase3Test {
    private fun campaign(
        id: String = "c1",
        code: String = "K4F2A",
        name: String = "حملة الأسبوع",
        start: String? = null,
        end: String? = null,
        status: CampaignStatus = CampaignStatus.ACTIVE,
    ) = Campaign(id, code, null, name, "", start, end, status, 1L, 1L)

    private fun post(
        id: String,
        scheduledAt: String? = null,
        status: ContentStatus = ContentStatus.DRAFT,
        updatedAt: Long = 1L,
        productName: String = "سائل جلي",
    ) = SavedContent(
        id = id, productId = "i1", productName = productName, channel = Channel.WHATSAPP, text = "نص $id",
        status = status, designId = 0, picturePath = null, scheduledAt = scheduledAt, campaignId = null,
        campaignCode = null, tone = null, angle = null, createdAt = 1L, updatedAt = updatedAt,
    )

    private fun row(product: Product, active: Boolean = true) = ProductRow(product, active)

    // ---------------------------------------------------------------- campaign codes and rules
    @Test
    fun codesAreShortReadableAndNeverReused() {
        val random = Random(7)
        val taken = HashSet<String>()
        repeat(500) {
            val code = CampaignRules.newCode(taken, random)
            assertEquals(CampaignRules.CODE_LENGTH, code.length)
            assertTrue(code.all { it in "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" })
            assertTrue(taken.add(code))
        }
    }

    @Test
    fun aCodeAlreadyTakenIsSkipped() {
        val first = CampaignRules.newCode(emptySet(), Random(1))
        val second = CampaignRules.newCode(setOf(first), Random(1))
        assertTrue(first != second)
    }

    @Test
    fun datesMustBeRealDays() {
        assertTrue(CampaignRules.isDate("2026-02-28"))
        assertFalse(CampaignRules.isDate("2026-02-30"))
        assertFalse(CampaignRules.isDate("2026-13-01"))
        assertFalse(CampaignRules.isDate("26-1-1"))
        assertFalse(CampaignRules.isDate(null))
    }

    @Test
    fun theCampaignFormIsChecked() {
        assertEquals(emptyList<String>(), CampaignRules.validate("عرض رمضان", "2026-02-18", "2026-03-19"))
        assertEquals(emptyList<String>(), CampaignRules.validate("بلا تواريخ", null, null))
        assertEquals(listOf("اكتب اسم الحملة"), CampaignRules.validate("  ", null, null))
        assertEquals(listOf("تاريخ النهاية قبل تاريخ البداية"), CampaignRules.validate("س", "2026-03-01", "2026-02-01"))
        assertEquals(listOf("تاريخ البداية غير صالح"), CampaignRules.validate("س", "2026-02-30", null))
    }

    @Test
    fun everyChannelGetsATrackingLinkWithTheCode() {
        val links = CampaignRules.trackingLinks(Brand(), "K4F2A")
        assertEquals(Channel.entries.size, links.size)
        for (link in links) {
            assertTrue(link.url.startsWith("https://"))
            if (link.channel.isWhatsApp) {
                assertTrue(link.url.startsWith("https://wa.me/967730487946?text="))
                assertTrue(link.url.contains("K4F2A"))
            } else {
                assertEquals("https://cadyco.web.app/?c=K4F2A&ch=" + link.channel.code, link.url)
            }
        }
    }

    @Test
    fun aCampaignKnowsWhereItStands() {
        val today = "2026-11-16"
        assertEquals(CampaignPhase.RUNNING, campaign().phase(today))
        assertEquals(CampaignPhase.RUNNING, campaign(start = "2026-11-16", end = "2026-11-16").phase(today))
        assertEquals(CampaignPhase.UPCOMING, campaign(start = "2026-11-17").phase(today))
        assertEquals(CampaignPhase.OVERDUE, campaign(end = "2026-11-15").phase(today))
        assertEquals(CampaignPhase.PAUSED, campaign(status = CampaignStatus.PAUSED).phase(today))
        assertEquals(CampaignPhase.ENDED, campaign(end = "2026-12-01", status = CampaignStatus.ENDED).phase(today))
    }

    @Test
    fun aCampaignCoversItsDatesOnly() {
        val c = campaign(start = "2026-11-10", end = "2026-11-20")
        assertTrue(c.coversDate("2026-11-10"))
        assertTrue(c.coversDate("2026-11-20"))
        assertFalse(c.coversDate("2026-11-09"))
        assertFalse(c.coversDate("2026-11-21"))
        assertFalse(campaign().coversDate("2026-11-16"))
        assertTrue(campaign(start = "2026-11-10").coversDate("2030-01-01"))
        assertEquals("2026-11-10 ← 2026-11-20", c.periodText)
        assertEquals("بلا تواريخ", campaign().periodText)
    }

    // ---------------------------------------------------------------- calendar arithmetic
    @Test
    fun theMonthGridStartsOnSaturday() {
        val grid = CalendarMath.monthGrid(2026, 11)
        assertEquals(35, grid.size)
        assertEquals("2026-10-31", grid.first().date)
        assertFalse(grid.first().inMonth)
        assertEquals("2026-11-01", grid[1].date)
        assertTrue(grid[1].inMonth)
        assertEquals("2026-12-04", grid.last().date)
        assertEquals(30, grid.count { it.inMonth })
    }

    @Test
    fun aMonthCanNeedSixWeeksOrFive() {
        assertEquals(42, CalendarMath.monthGrid(2026, 5).size)
        assertEquals(35, CalendarMath.monthGrid(2026, 2).size)
        assertEquals("2026-01-31", CalendarMath.monthGrid(2026, 2).first().date)
        assertEquals("2026-03-06", CalendarMath.monthGrid(2026, 2).last().date)
        assertEquals(28, CalendarMath.daysInMonth(2026, 2))
        assertEquals(29, CalendarMath.daysInMonth(2028, 2))
    }

    @Test
    fun weekdaysAndDistancesAreRight() {
        assertEquals(0, CalendarMath.weekdayIndex("2026-10-31"))
        assertEquals("الاثنين", CalendarMath.weekdayName("2026-11-16"))
        assertEquals("الجمعة", CalendarMath.weekdayName("2026-11-20"))
        assertEquals("الاثنين 16 نوفمبر 2026", CalendarMath.longDate("2026-11-16"))
        assertEquals(4, CalendarMath.daysBetween("2026-11-16", "2026-11-20"))
        assertEquals(-1, CalendarMath.daysBetween("2026-11-16", "2026-11-15"))
        assertNull(CalendarMath.daysBetween("x", "2026-11-15"))
        assertEquals("2026-12-01", CalendarMath.addDays("2026-11-30", 1))
        assertEquals("2027-01-01", CalendarMath.addDays("2026-12-31", 1))
        assertEquals("2026-02-28", CalendarMath.addDays("2026-03-01", -1))
    }

    @Test
    fun monthsMoveAcrossYears() {
        assertEquals(2027 to 1, CalendarMath.addMonths(2026, 12, 1))
        assertEquals(2025 to 12, CalendarMath.addMonths(2026, 1, -1))
        assertEquals(2026 to 11, CalendarMath.addMonths(2026, 11, 0))
        assertEquals(2027 to 5, CalendarMath.addMonths(2026, 11, 6))
        assertEquals("2026-11-01" to "2026-11-30", CalendarMath.monthRange(2026, 11))
        assertEquals("نوفمبر 2026", CalendarMath.monthTitle(2026, 11))
    }

    @Test
    fun datesBetweenIncludesBothEnds() {
        assertEquals(listOf("2026-11-29", "2026-11-30", "2026-12-01"), CalendarMath.datesBetween("2026-11-29", "2026-12-01"))
        assertEquals(emptyList<String>(), CalendarMath.datesBetween("2026-12-01", "2026-11-29"))
        assertEquals(5, CalendarMath.datesBetween("2026-01-01", "2030-01-01", limit = 5).size)
    }

    // ---------------------------------------------------------------- built-in occasions
    @Test
    fun theTabularCalendarIsWithinADayOfUmmAlQura() {
        val reference = mapOf(
            "بداية شهر رمضان" to listOf("2026-02-18", "2027-02-08", "2028-01-28"),
            "عيد الفطر" to listOf("2026-03-20", "2027-03-09", "2028-02-26"),
            "يوم عرفة" to listOf("2026-05-26", "2027-05-15", "2028-05-04"),
            "عيد الأضحى" to listOf("2026-05-27", "2027-05-16", "2028-05-05"),
            "رأس السنة الهجرية" to listOf("2026-06-16", "2027-06-06"),
            "المولد النبوي الشريف" to listOf("2026-08-25", "2027-08-14"),
        )
        for ((title, expectedDates) in reference) {
            for (expected in expectedDates) {
                val year = CalendarMath.yearOf(expected)!!
                val found = HijriCalendar.occasions(year).firstOrNull { it.title == title && CalendarMath.daysBetween(expected, it.date)!! in -1..1 }
                assertNotNull("$title $expected", found)
            }
        }
    }

    @Test
    fun aYearHasItsSixBuiltInOccasionsInOrder() {
        val list = HijriCalendar.occasions(2026)
        assertEquals(6, list.size)
        assertEquals(list.sortedBy { it.date }, list)
        assertTrue(list.all { it.builtIn && it.id == null })
        assertEquals("2026-02-18", list.first { it.title == "بداية شهر رمضان" }.date)
    }

    // ---------------------------------------------------------------- the calendar's days
    @Test
    fun postsCampaignsAndOccasionsLandOnTheirDays() {
        val posts = listOf(
            post("a", "2026-11-20T09:00", ContentStatus.APPROVED),
            post("b", "2026-11-20T08:00", ContentStatus.DRAFT),
            post("old", "2026-11-20T10:00", ContentStatus.ARCHIVED),
            post("none", null),
        )
        val running = campaign("c1", start = "2026-11-15", end = "2026-11-25")
        val ended = campaign("c2", code = "ZZ9AB", start = "2026-11-10", end = "2026-11-30", status = CampaignStatus.ENDED)
        val yearly = Occasion("o1", "يوم الشركة", "2025-11-30", true, 1L, 1L)
        val once = Occasion("o2", "افتتاح الفرع", "2026-11-18", false, 1L, 1L)
        val days = CalendarEntries.build("2026-10-31", "2026-12-05", posts, listOf(running, ended), listOf(yearly, once), "2026-11-16")

        assertEquals(36, days.size)
        assertEquals(listOf("b", "a"), days.getValue("2026-11-20").content.map { it.id })
        assertEquals(listOf("c1"), days.getValue("2026-11-25").campaigns.map { it.id })
        assertEquals(listOf("c1", "c2"), days.getValue("2026-11-16").campaigns.map { it.id })
        assertEquals(listOf("c1"), days.getValue("2026-11-17").campaigns.map { it.id })
        assertTrue(days.getValue("2026-11-26").campaigns.isEmpty())
        assertEquals(listOf("يوم الشركة"), days.getValue("2026-11-30").occasions.map { it.title })
        assertEquals(listOf("افتتاح الفرع"), days.getValue("2026-11-18").occasions.map { it.title })
        assertTrue(days.getValue("2026-12-05").isEmpty)
    }

    // ---------------------------------------------------------------- home
    @Test
    fun theHomeCountsAndWarns() {
        val soon = Product("p1", "جلي ليمون", "كرتونة", 7000L, 6800L, "2026-11-18", picturePath = "z.jpg")
        val far = Product("p2", "منعّم", "جركن", 1800L, 1500L, "2026-12-30", picturePath = "x.jpg")
        val plain = Product("p3", "صابون", "علبة", 900L, picturePath = "y.jpg")
        val products = listOf(row(soon), row(far), row(plain), row(soon.copy(id = "p4", name = "قديم"), active = false))
        val may = DateUtil.toUtcMillis("2026-11-05")!!
        val content = listOf(
            post("late", "2026-11-15T09:00", ContentStatus.APPROVED),
            post("pend", "2026-11-17T09:00", ContentStatus.PENDING),
            post("draft", "2026-11-20T09:00", ContentStatus.DRAFT),
            post("pub", "2026-11-05T09:00", ContentStatus.PUBLISHED, updatedAt = may),
            post("arch", "2026-11-01T09:00", ContentStatus.ARCHIVED),
        )
        val campaigns = listOf(
            campaign("run", name = "الأسبوع", start = "2026-11-10", end = "2026-11-17"),
            campaign("over", name = "قديمة", end = "2026-11-10"),
            campaign("paused", status = CampaignStatus.PAUSED),
        )
        val insights = HomeInsightsEngine.compute(content, campaigns, products, "2026-11-16", "2026-11-16T12:00")
        val stats = insights.stats
        assertEquals(1, stats.runningCampaigns)
        assertEquals(1, stats.drafts)
        assertEquals(1, stats.pending)
        assertEquals(1, stats.approved)
        assertEquals(2, stats.scheduledNextWeek)

        val titles = insights.alerts.map { it.title }
        assertEquals(AlertLevel.URGENT, insights.alerts.first().level)
        assertEquals("منشور فات موعد نشره", titles.first())
        assertTrue(titles.contains("منشور بانتظار الاعتماد"))
        assertTrue(titles.contains("ينتهي عرض جلي ليمون بعد يومين"))
        assertTrue(titles.contains("حملة «قديمة» تجاوزت موعد نهايتها"))
        assertTrue(titles.contains("تنتهي حملة «الأسبوع» غدًا"))
        assertFalse(titles.any { it.contains("بلا صورة") })
        val levels = insights.alerts.map { it.level.ordinal }
        assertEquals(levels.sorted(), levels)
        assertEquals("p1", insights.alerts.first { it.target == AlertTarget.STUDIO }.productId)
    }

    @Test
    fun aQuietDayHasNoAlerts() {
        val product = Product("p1", "جلي", "كرتونة", 7000L, picturePath = "a.jpg")
        val insights = HomeInsightsEngine.compute(emptyList(), emptyList(), listOf(row(product)), "2026-11-16", "2026-11-16T08:00")
        assertTrue(insights.alerts.isEmpty())
        assertEquals(0, insights.stats.drafts)
    }

    @Test
    fun productsWithoutPicturesAreCounted() {
        val a = Product("a", "أ", "ع", 100L)
        val b = Product("b", "ب", "ع", 100L)
        val insights = HomeInsightsEngine.compute(emptyList(), emptyList(), listOf(row(a), row(b)), "2026-11-16", "2026-11-16T08:00")
        assertEquals("2 أصناف بلا صورة مرتبطة", insights.alerts.single().title)
        assertEquals(AlertTarget.PRODUCTS, insights.alerts.single().target)
    }

    @Test
    fun theIdeaOfTheDayIsTheSameEveryTimeItIsAsked() {
        val products = listOf(TestFixtures.offer, TestFixtures.plain)
        val bank = DefaultIdeas.byAngle
        val first = DailyIdeas.pick("2026-11-16", products, bank, Brand())
        val again = DailyIdeas.pick("2026-11-16", products, bank, Brand())
        assertNotNull(first)
        assertEquals(first, again)
        val picks = (0 until 10).map { DailyIdeas.pick(CalendarMath.addDays("2026-11-16", it), products, bank, Brand()) }
        assertTrue(picks.all { it != null && it.idea.isNotBlank() })
        assertTrue(picks.map { it!!.idea }.distinct().size > 1)
        assertNull(DailyIdeas.pick("2026-11-16", emptyList(), bank, Brand()))
        assertNull(DailyIdeas.pick("2026-11-16", products, emptyMap(), Brand()))
        assertNull(DailyIdeas.pick("not a date", products, bank, Brand()))
    }

    // ---------------------------------------------------------------- library
    @Test
    fun theLibrarySearchesProductTextAndCampaignCode() {
        val posts = listOf(
            post("a", productName = "سائل جلي ليمون").copy(text = "عرض الأسبوع", status = ContentStatus.APPROVED),
            post("b", productName = "منعّم").copy(text = "نص آخر", campaignCode = "K4F2A"),
            post("c", productName = "صابون").copy(text = "نص ثالث", status = ContentStatus.ARCHIVED),
        )
        assertEquals(3, LibraryRules.filter(posts, null, "").size)
        assertEquals(listOf("a"), LibraryRules.filter(posts, null, "ليمون").map { it.id })
        assertEquals(listOf("a"), LibraryRules.filter(posts, null, "  عرض ").map { it.id })
        assertEquals(listOf("b"), LibraryRules.filter(posts, null, "k4f2a").map { it.id })
        assertEquals(listOf("c"), LibraryRules.filter(posts, ContentStatus.ARCHIVED, "").map { it.id })
        assertEquals(emptyList<String>(), LibraryRules.filter(posts, ContentStatus.PUBLISHED, ""))
        assertEquals(emptyList<String>(), LibraryRules.filter(posts, ContentStatus.ARCHIVED, "ليمون"))
        val counts = LibraryRules.counts(posts)
        assertEquals(ContentStatus.entries.size, counts.size)
        assertEquals(1, counts.getValue(ContentStatus.APPROVED))
        assertEquals(0, counts.getValue(ContentStatus.PUBLISHED))
    }

    @Test
    fun noStatusChangeCanSkipTheApprovalCheck() {
        for (status in ContentStatus.entries) {
            val targets = LibraryRules.moves(status).map { it.target }
            assertFalse("from $status", targets.contains(ContentStatus.APPROVED))
            assertFalse("from $status", targets.contains(status))
            assertTrue("from $status", targets.isNotEmpty())
        }
        assertEquals(
            listOf(ContentStatus.PUBLISHED, ContentStatus.DRAFT, ContentStatus.ARCHIVED),
            LibraryRules.moves(ContentStatus.APPROVED).map { it.target },
        )
        assertEquals(listOf(ContentStatus.DRAFT), LibraryRules.moves(ContentStatus.ARCHIVED).map { it.target })
    }

    @Test
    fun theClockHasTheShapeOfASchedule() {
        val now = DateUtil.nowMinute()
        assertEquals(16, now.length)
        assertEquals('T', now[10])
        assertEquals('-', now[4])
        assertEquals('-', now[7])
        assertEquals(':', now[13])
    }
}
