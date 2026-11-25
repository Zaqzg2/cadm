package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Campaign
import com.cady.cadymarketing.domain.model.CampaignStatus
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.SavedContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetSummaryTest {
    private val today = "2026-11-24"
    private val now = "2026-11-24T10:00"

    private fun post(id: String, status: ContentStatus, scheduledAt: String?, name: String = "منتج $id") = SavedContent(
        id = id,
        productId = "p-$id",
        productName = name,
        channel = Channel.WHATSAPP,
        text = "نص",
        status = status,
        designId = 0,
        picturePath = null,
        scheduledAt = scheduledAt,
        campaignId = null,
        campaignCode = null,
        tone = null,
        angle = null,
        createdAt = 1L,
        updatedAt = 1L,
    )

    private fun campaign(id: String, status: CampaignStatus, start: String? = null, end: String? = null) = Campaign(
        id = id,
        code = "C$id",
        number = null,
        name = "حملة $id",
        goal = "",
        startDate = start,
        endDate = end,
        status = status,
        createdAt = 1L,
        updatedAt = 1L,
    )

    @Test
    fun nothingStoredGivesZerosAndNoNextPost() {
        val summary = WidgetSummaryEngine.compute(emptyList(), emptyList(), today, now)
        assertEquals(0, summary.drafts)
        assertEquals(0, summary.pending)
        assertEquals(0, summary.approved)
        assertEquals(0, summary.scheduledNextWeek)
        assertEquals(0, summary.runningCampaigns)
        assertEquals(0, summary.late)
        assertNull(summary.next)
        assertEquals(CalendarMath.longDate(today), summary.dateLabel)
    }

    @Test
    fun theCountsAreTheHomeScreensCounts() {
        val posts = listOf(
            post("1", ContentStatus.DRAFT, null),
            post("2", ContentStatus.DRAFT, "2026-11-26T09:00"),
            post("3", ContentStatus.PENDING, null),
            post("4", ContentStatus.APPROVED, "2026-11-25T18:00"),
            post("5", ContentStatus.PUBLISHED, "2026-11-20T08:00"),
        )
        val campaigns = listOf(campaign("1", CampaignStatus.ACTIVE), campaign("2", CampaignStatus.ENDED))
        val summary = WidgetSummaryEngine.compute(posts, campaigns, today, now)
        val home = HomeInsightsEngine.compute(posts, campaigns, emptyList(), today, now).stats
        assertEquals(home.drafts, summary.drafts)
        assertEquals(home.pending, summary.pending)
        assertEquals(home.approved, summary.approved)
        assertEquals(home.scheduledNextWeek, summary.scheduledNextWeek)
        assertEquals(home.runningCampaigns, summary.runningCampaigns)
        assertEquals(2, summary.drafts)
        assertEquals(1, summary.runningCampaigns)
    }

    @Test
    fun onlyUnpublishedPostsWhoseTimePassedAreLate() {
        val posts = listOf(
            post("1", ContentStatus.APPROVED, "2026-11-24T09:59"),
            post("2", ContentStatus.DRAFT, "2026-11-23T08:00"),
            post("3", ContentStatus.PUBLISHED, "2026-11-20T08:00"),
            post("4", ContentStatus.ARCHIVED, "2026-11-19T08:00"),
            post("5", ContentStatus.APPROVED, "2026-11-24T10:00"),
            post("6", ContentStatus.APPROVED, null),
        )
        assertEquals(2, WidgetSummaryEngine.compute(posts, emptyList(), today, now).late)
    }

    @Test
    fun theNextPostIsTheEarliestOneStillToCome() {
        val posts = listOf(
            post("late", ContentStatus.APPROVED, "2026-11-24T08:00", "فات موعده"),
            post("b", ContentStatus.APPROVED, "2026-11-25T09:00", "الثاني"),
            post("a", ContentStatus.DRAFT, "2026-11-24T14:30", "الأول"),
            post("done", ContentStatus.PUBLISHED, "2026-11-24T11:00", "منشور"),
        )
        val next = WidgetSummaryEngine.compute(posts, emptyList(), today, now).next
        assertNotNull(next)
        assertEquals("الأول", next!!.title)
        assertEquals("اليوم 14:30", next.whenLabel)
    }

    @Test
    fun aPostRightNowCountsAsComingUp() {
        val posts = listOf(post("1", ContentStatus.APPROVED, "2026-11-24T10:00", "الآن"))
        assertEquals("الآن", WidgetSummaryEngine.compute(posts, emptyList(), today, now).next?.title)
    }

    @Test
    fun theTimeIsNamedByDay() {
        assertEquals("اليوم 09:05", WidgetSummaryEngine.whenLabel("2026-11-24T09:05", today))
        assertEquals("غدًا 18:00", WidgetSummaryEngine.whenLabel("2026-11-25T18:00", today))
        val later = WidgetSummaryEngine.whenLabel("2026-11-28T07:30", today)
        assertEquals(CalendarMath.weekdayName("2026-11-28") + " 11-28 07:30", later)
    }

    @Test
    fun aScheduleWithNoTimeStillReadsWell() {
        assertEquals("اليوم", WidgetSummaryEngine.whenLabel("2026-11-24", today))
        assertEquals("غدًا", WidgetSummaryEngine.whenLabel("2026-11-25", today))
    }
}
