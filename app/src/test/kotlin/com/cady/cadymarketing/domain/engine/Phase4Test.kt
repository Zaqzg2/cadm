package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.AttributedOrder
import com.cady.cadymarketing.domain.model.Campaign
import com.cady.cadymarketing.domain.model.CampaignStatus
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.Consent
import com.cady.cadymarketing.domain.model.ConsentStatus
import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.Customer
import com.cady.cadymarketing.domain.model.Exclusion
import com.cady.cadymarketing.domain.model.ManualResult
import com.cady.cadymarketing.domain.model.OrderOrigin
import com.cady.cadymarketing.domain.model.ResultMetric
import com.cady.cadymarketing.domain.model.SavedContent
import com.cady.cadymarketing.domain.model.Segment
import com.cady.cadymarketing.domain.model.SegmentKind
import com.cady.cadymarketing.domain.model.SendStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4Test {
    private fun customer(
        id: String,
        name: String = "أحمد سالم",
        phone: String? = "967730487946",
        area: String = "صنعاء",
        rep: String = "",
        active: Boolean = true,
    ) = Customer(id, name, phone, phone.orEmpty(), area, rep, active)

    private fun consent(id: String, status: ConsentStatus) = Consent(id, status, "", 1L)

    private fun segment(
        kind: SegmentKind = SegmentKind.FILTER,
        area: String = "",
        rep: String = "",
        members: List<String> = emptyList(),
    ) = Segment("s1", "شريحة", kind, area, rep, members, 1L, 1L)

    // ---------------------------------------------------------------- phone numbers
    @Test
    fun yemeniMobilesBecomeInternationalDigits() {
        assertEquals("967730487946", PhoneRules.normalize("730487946"))
        assertEquals("967730487946", PhoneRules.normalize("0730487946"))
        assertEquals("967730487946", PhoneRules.normalize("+967 730 487 946"))
        assertEquals("967730487946", PhoneRules.normalize("00967730487946"))
        assertEquals("967730487946", PhoneRules.normalize("967-730-487-946"))
        assertEquals("967771234567", PhoneRules.normalize("٠٧٧١٢٣٤٥٦٧"))
    }

    @Test
    fun otherCountriesAreKeptAndBadNumbersRejected() {
        assertEquals("966501234567", PhoneRules.normalize("+966 50 123 4567"))
        assertNull(PhoneRules.normalize(null))
        assertNull(PhoneRules.normalize(""))
        assertNull(PhoneRules.normalize("لا يوجد"))
        assertNull(PhoneRules.normalize("123456"))
        assertNull(PhoneRules.normalize("01234567"))
        assertNull(PhoneRules.normalize("96712345"))
        assertNull(PhoneRules.normalize("967130487946"))
        assertNull(PhoneRules.normalize("1234567890123456"))
    }

    @Test
    fun chatLinkCarriesTheEncodedPersonalText() {
        val link = PhoneRules.waLink("967730487946", "مرحبا {x}\nسطر")
        assertTrue(link.startsWith("https://wa.me/967730487946?text="))
        assertFalse(link.contains(" "))
        assertFalse(link.contains("\n"))
        assertTrue(link.endsWith("%D8%B3%D8%B7%D8%B1"))
        assertTrue(link.contains("%0A"))
    }

    // ---------------------------------------------------------------- names and personal text
    @Test
    fun firstNameKeepsTitlesAndKunyaWithTheName() {
        assertEquals("أحمد", AudienceEngine.firstName("أحمد سالم الحداد"))
        assertEquals("الحاج علي", AudienceEngine.firstName("الحاج علي محمد"))
        assertEquals("أبو أحمد", AudienceEngine.firstName("أبو أحمد السقاف"))
        assertEquals("الحاج أبو علي", AudienceEngine.firstName("الحاج أبو علي الشامي"))
        assertEquals("مؤسسة النور", AudienceEngine.firstName("مؤسسة النور للتجارة"))
        assertEquals("سالم", AudienceEngine.firstName("  سالم  "))
        assertEquals("", AudienceEngine.firstName("   "))
        // A name made only of a title is not extended past the words that exist.
        assertEquals("الحاج", AudienceEngine.firstName("الحاج"))
    }

    @Test
    fun personalizeFillsTheNameOrAFriendlyFallback() {
        val text = "أهلًا ${com.cady.cadymarketing.domain.engine.Placeholders.CUSTOMER_NAME}، عرض اليوم"
        assertEquals("أهلًا أحمد، عرض اليوم", AudienceEngine.personalize(text, customer("a", name = "أحمد سالم")))
        assertEquals("أهلًا عميلنا العزيز، عرض اليوم", AudienceEngine.personalize(text, customer("b", name = " ")))
        assertEquals("نص بلا اسم", AudienceEngine.personalize("نص بلا اسم", customer("c")))
        assertTrue(AudienceEngine.hasNamePlaceholder(text))
        assertFalse(AudienceEngine.hasNamePlaceholder("نص بلا اسم"))
    }

    @Test
    fun chatLinkIsNullWithoutAUsableNumber() {
        assertNull(AudienceEngine.chatLink("نص", customer("a", phone = null)))
        val link = AudienceEngine.chatLink("أهلًا {اسم العميل}", customer("b", name = "سالم علي"))
        assertNotNull(link)
        assertTrue(link!!.startsWith("https://wa.me/967730487946?text="))
        assertFalse(link.contains("%7B"))
    }

    // ---------------------------------------------------------------- segments and consent
    @Test
    fun filterSegmentsMatchAreaAndRep() {
        val all = listOf(
            customer("1", area = "صنعاء - حدة", rep = "علي"),
            customer("2", area = "عدن", rep = "علي"),
            customer("3", area = "صنعاء - الحصبة", rep = "سالم"),
        )
        assertEquals(listOf("1", "3"), AudienceEngine.members(segment(area = "صنعاء"), all).map { it.id })
        assertEquals(listOf("1", "2"), AudienceEngine.members(segment(rep = "علي"), all).map { it.id })
        assertEquals(listOf("1"), AudienceEngine.members(segment(area = "صنعاء", rep = "علي"), all).map { it.id })
        assertEquals(listOf("1", "2", "3"), AudienceEngine.members(segment(), all).map { it.id })
        assertTrue(AudienceEngine.members(segment(area = "تعز"), all).isEmpty())
    }

    @Test
    fun manualSegmentsKeepOnlyThePickedCustomers() {
        val all = listOf(customer("1"), customer("2"), customer("3"))
        val picked = segment(SegmentKind.MANUAL, area = "تجاهل", members = listOf("3", "1", "غائب"))
        assertEquals(listOf("1", "3"), AudienceEngine.members(picked, all).map { it.id })
    }

    @Test
    fun noConsentMeansNoSending() {
        assertEquals(Exclusion.NO_CONSENT, AudienceEngine.exclusion(customer("1"), null))
        assertEquals(Exclusion.NO_CONSENT, AudienceEngine.exclusion(customer("1"), consent("1", ConsentStatus.UNKNOWN)))
        assertEquals(Exclusion.DECLINED, AudienceEngine.exclusion(customer("1"), consent("1", ConsentStatus.DECLINED)))
        // A refusal wins even when the customer has no number and is inactive.
        assertEquals(
            Exclusion.DECLINED,
            AudienceEngine.exclusion(customer("1", phone = null, active = false), consent("1", ConsentStatus.DECLINED)),
        )
        assertEquals(Exclusion.INACTIVE, AudienceEngine.exclusion(customer("1", active = false), consent("1", ConsentStatus.GRANTED)))
        assertEquals(Exclusion.NO_PHONE, AudienceEngine.exclusion(customer("1", phone = null), consent("1", ConsentStatus.GRANTED)))
        assertNull(AudienceEngine.exclusion(customer("1"), consent("1", ConsentStatus.GRANTED)))
    }

    @Test
    fun partitionSeparatesWhoCanBeSentTo() {
        val members = listOf(customer("1"), customer("2"), customer("3", phone = null), customer("4"))
        val consents = mapOf(
            "1" to consent("1", ConsentStatus.GRANTED),
            "2" to consent("2", ConsentStatus.DECLINED),
            "3" to consent("3", ConsentStatus.GRANTED),
        )
        val result = AudienceEngine.partition(members, consents)
        assertEquals(listOf("1"), result.eligible.map { it.id })
        assertEquals(
            listOf("2" to Exclusion.DECLINED, "3" to Exclusion.NO_PHONE, "4" to Exclusion.NO_CONSENT),
            result.excluded.map { it.first.id to it.second },
        )
    }

    @Test
    fun segmentFormIsValidated() {
        assertTrue(AudienceEngine.validate("عملاء الجملة", SegmentKind.FILTER, emptyList()).isEmpty())
        assertEquals(1, AudienceEngine.validate(" ", SegmentKind.FILTER, emptyList()).size)
        assertEquals(1, AudienceEngine.validate("قائمة", SegmentKind.MANUAL, emptyList()).size)
        assertTrue(AudienceEngine.validate("قائمة", SegmentKind.MANUAL, listOf("1")).isEmpty())
    }

    @Test
    fun progressCountsOnlyThoseWhoCanBeSentTo() {
        val eligible = listOf(customer("1"), customer("2"), customer("3"))
        val marks = mapOf("1" to SendStatus.SENT, "3" to SendStatus.SKIPPED, "9" to SendStatus.SENT)
        val progress = AudienceEngine.progress(eligible, marks)
        assertEquals(2, progress.done)
        assertEquals(3, progress.total)
        assertFalse(progress.finished)
        assertTrue(AudienceEngine.progress(eligible, marks + ("2" to SendStatus.SENT)).finished)
        assertFalse(AudienceEngine.progress(emptyList(), emptyMap()).finished)
    }

    // ---------------------------------------------------------------- reading Kady's customers
    @Test
    fun customersAreReadUnderTheirLikelyNames() {
        val ok = CustomerMapper.customer(
            "c1",
            mapOf("name" to "  الحاج علي ", "mobile" to "0730487946", "city" to "صنعاء", "repName" to "سالم", "isActive" to true),
        ) as Mapped.Ok
        assertEquals("الحاج علي", ok.value.name)
        assertEquals("967730487946", ok.value.phone)
        assertEquals("0730487946", ok.value.rawPhone)
        assertEquals("صنعاء", ok.value.area)
        assertEquals("سالم", ok.value.repName)
        assertTrue(ok.value.active)

        val other = CustomerMapper.customer("c2", mapOf("customerName" to "مطعم النخيل", "whatsapp" to 730487946L)) as Mapped.Ok
        assertEquals("967730487946", other.value.phone)
    }

    @Test
    fun customersWithoutNameAreSkippedAndWithoutPhoneKept() {
        assertTrue(CustomerMapper.customer("c1", mapOf("phone" to "730487946")) is Mapped.Skipped)
        val noPhone = CustomerMapper.customer("c2", mapOf("name" to "سعيد")) as Mapped.Ok
        assertNull(noPhone.value.phone)
        assertEquals("", noPhone.value.rawPhone)
    }

    @Test
    fun removedAndInactiveCustomersAreNotActive() {
        fun active(extra: Map<String, Any?>) =
            (CustomerMapper.customer("c", mapOf("name" to "س") + extra) as Mapped.Ok).value.active
        assertTrue(active(emptyMap()))
        assertFalse(active(mapOf("isActive" to false)))
        assertFalse(active(mapOf("isDeleted" to true)))
        assertFalse(active(mapOf("archived" to "true")))
        assertTrue(active(mapOf("active" to true, "deleted" to false)))
    }

    // ---------------------------------------------------------------- reading orders
    @Test
    fun campaignCodesAreCompared_ignoringCaseAndSpaces() {
        assertEquals("K4F2A", OrderMapper.code(" k4f-2a "))
        assertNull(OrderMapper.code(""))
        assertNull(OrderMapper.code(null))
        assertNull(OrderMapper.code("--"))
    }

    @Test
    fun anOrderIsReadWithItsChannelAmountAndDate() {
        val data = mapOf<String, Any?>(
            "campaignCode" to "k4f2a",
            "ch" to "ig",
            "total" to "12,500",
            "createdAt" to 1_790_000_000_000.0,
            "status" to "new",
        )
        val ok = OrderMapper.map(OrderOrigin.SITE, "o1", data, "campaignCode") as Mapped.Ok
        assertEquals("site:o1", ok.value.id)
        assertEquals("K4F2A", ok.value.campaignCode)
        assertEquals(Channel.INSTAGRAM, ok.value.channel)
        assertEquals(12_500L, ok.value.amount)
        assertNotNull(ok.value.date)
        assertFalse(ok.value.cancelled)
    }

    @Test
    fun channelNamesAndCancelledStatusesAreUnderstood() {
        assertEquals(Channel.WHATSAPP, OrderMapper.channelOf("WhatsApp"))
        assertEquals(Channel.TIKTOK, OrderMapper.channelOf("تيك توك"))
        assertEquals(Channel.WHATSAPP_STATUS, OrderMapper.channelOf("st"))
        assertNull(OrderMapper.channelOf("telegram"))
        assertNull(OrderMapper.channelOf(5))

        fun cancelled(status: String) =
            (OrderMapper.map(OrderOrigin.KADY, "i", mapOf("code" to "ABCDE", "status" to status), "code") as Mapped.Ok).value.cancelled
        assertTrue(cancelled("Cancelled"))
        assertTrue(cancelled("ملغي"))
        assertTrue(cancelled("refunded"))
        assertFalse(cancelled("delivered"))
        assertFalse(cancelled("جديد"))
    }

    @Test
    fun anOrderWithoutACodeIsSkippedWithTheFieldNamed() {
        val skipped = OrderMapper.map(OrderOrigin.SITE, "o9", mapOf("total" to 100), "campaignCode") as Mapped.Skipped
        assertTrue(skipped.reason.contains("campaignCode"))
        assertTrue(skipped.reason.contains("o9"))
    }

    // ---------------------------------------------------------------- the report
    private fun campaign(id: String, code: String, name: String = "حملة $id") =
        Campaign(id, code, null, name, "", null, null, CampaignStatus.ACTIVE, 1L, 1L)

    private fun order(
        id: String,
        code: String,
        origin: OrderOrigin = OrderOrigin.SITE,
        channel: Channel? = null,
        amount: Long? = null,
        date: String? = "2026-11-10",
        cancelled: Boolean = false,
    ) = AttributedOrder(id, origin, code, channel, amount, date, cancelled)

    private fun result(
        id: String,
        campaignId: String,
        metric: ResultMetric,
        value: Long,
        channel: Channel? = null,
        date: String = "2026-11-10",
    ) = ManualResult(id, campaignId, channel, metric, value, date, "", 1L, 1L)

    private fun post(id: String, campaignId: String?, status: ContentStatus) = SavedContent(
        id = id, productId = "p", productName = "صنف", channel = Channel.WHATSAPP, text = "نص", status = status,
        designId = 0, picturePath = null, scheduledAt = null, campaignId = campaignId, campaignCode = null,
        tone = null, angle = null, createdAt = 1L, updatedAt = 1L,
    )

    private val campaigns = listOf(campaign("c1", "K4F2A", "عرض الجلي"), campaign("c2", "M7P3Q", "رمضان"))

    @Test
    fun ordersAndRevenueAreGroupedPerCampaign() {
        val orders = listOf(
            order("1", "K4F2A", channel = Channel.WHATSAPP, amount = 7000),
            order("2", "K4F2A", channel = Channel.INSTAGRAM, amount = 5000),
            order("3", "K4F2A", OrderOrigin.KADY, Channel.WHATSAPP, 3000),
            order("4", "M7P3Q", channel = Channel.FACEBOOK, amount = 2000),
        )
        val posts = listOf(
            post("a", "c1", ContentStatus.PUBLISHED), post("b", "c1", ContentStatus.DRAFT), post("c", "c2", ContentStatus.PUBLISHED),
            post("d", null, ContentStatus.PUBLISHED),
        )
        val summary = ReportEngine.compute(campaigns, posts, orders, emptyList())
        val first = summary.campaigns.first { it.campaign.id == "c1" }
        assertEquals(3L, first.orders)
        assertEquals(2, first.siteOrders)
        assertEquals(1, first.kadyOrders)
        assertEquals(15_000L, first.revenue)
        assertEquals(2, first.posts)
        assertEquals(1, first.published)
        assertEquals(listOf(Channel.WHATSAPP, Channel.INSTAGRAM), first.byChannel.map { it.channel })
        assertEquals(2, first.byChannel.first().orders)
        assertEquals(10_000L, first.byChannel.first().revenue)
        assertEquals(4L, summary.orders)
        assertEquals(17_000L, summary.revenue)
        assertEquals(2, summary.publishedPosts)
        assertEquals("c1", summary.campaigns.first().campaign.id)
        assertEquals(Channel.WHATSAPP, summary.bestChannel)
    }

    @Test
    fun cancelledOrdersAreKeptApartAndNeverCounted() {
        val orders = listOf(
            order("1", "K4F2A", amount = 7000),
            order("2", "K4F2A", amount = 9000, cancelled = true),
        )
        val first = ReportEngine.compute(campaigns, emptyList(), orders, emptyList()).campaigns.first { it.campaign.id == "c1" }
        assertEquals(1L, first.orders)
        assertEquals(7000L, first.revenue)
        assertEquals(1, first.cancelled)
    }

    @Test
    fun unknownCodesAreReportedAsOrphans() {
        val orders = listOf(order("1", "ZZZZZ"), order("2", "ZZZZZ"), order("3", "YYYYY"), order("4", "YYYYY", cancelled = true))
        val summary = ReportEngine.compute(campaigns, emptyList(), orders, emptyList())
        assertEquals(3, summary.orphanOrders)
        assertEquals(listOf("YYYYY", "ZZZZZ"), summary.orphanCodes)
        assertEquals(0L, summary.orders)
    }

    @Test
    fun resultsWrittenByHandAreAddedToTheCounted() {
        val orders = listOf(order("1", "K4F2A", channel = Channel.WHATSAPP, amount = 1000))
        val results = listOf(
            result("r1", "c1", ResultMetric.ORDERS, 4, Channel.WHATSAPP_STATUS),
            result("r2", "c1", ResultMetric.SALES, 20_000, Channel.WHATSAPP_STATUS),
            result("r3", "c1", ResultMetric.REACH, 1500),
            result("r4", "c1", ResultMetric.REPLIES, 12),
            result("r5", "c2", ResultMetric.ORDERS, 9),
        )
        val summary = ReportEngine.compute(campaigns, emptyList(), orders, results)
        val first = summary.campaigns.first { it.campaign.id == "c1" }
        assertEquals(5L, first.orders)
        assertEquals(21_000L, first.revenue)
        assertEquals(1500L, first.reach)
        assertEquals(12L, first.replies)
        assertEquals(Channel.WHATSAPP_STATUS, first.byChannel.first().channel)
        assertEquals(14L, summary.orders)
        assertEquals(Channel.WHATSAPP_STATUS, summary.bestChannel)
    }

    @Test
    fun aPeriodKeepsOnlyWhatFallsInsideIt() {
        val orders = listOf(
            order("1", "K4F2A", amount = 100, date = "2026-11-01"),
            order("2", "K4F2A", amount = 200, date = "2026-11-15"),
            order("3", "K4F2A", amount = 400, date = null),
        )
        val results = listOf(result("r1", "c1", ResultMetric.ORDERS, 3, date = "2026-11-16"))
        val all = ReportEngine.compute(campaigns, emptyList(), orders, results)
        assertEquals(700L, all.revenue)
        assertEquals(6L, all.orders)
        val week = ReportEngine.compute(campaigns, emptyList(), orders, results, "2026-11-10", "2026-11-18")
        assertEquals(200L, week.revenue)
        assertEquals(1L + 3L, week.orders)
        val open = ReportEngine.compute(campaigns, emptyList(), orders, results, from = "2026-11-15", to = null)
        assertEquals(200L, open.revenue)
    }

    @Test
    fun lastDaysIsInclusiveOfToday() {
        assertEquals("2026-11-12" to "2026-11-18", ReportEngine.lastDays("2026-11-18", 7))
        assertEquals("2026-10-20" to "2026-11-18", ReportEngine.lastDays("2026-11-18", 30))
    }

    @Test
    fun aCampaignWithNothingYetHasNoResult() {
        val summary = ReportEngine.compute(campaigns, emptyList(), emptyList(), emptyList())
        assertTrue(summary.campaigns.none { it.hasAnyResult })
        assertNull(summary.bestChannel)
        assertEquals(2, summary.campaigns.size)
    }
}
