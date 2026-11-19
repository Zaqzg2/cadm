package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.AttributedOrder
import com.cady.cadymarketing.domain.model.Campaign
import com.cady.cadymarketing.domain.model.CampaignReport
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.ChannelStat
import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.ManualResult
import com.cady.cadymarketing.domain.model.OrderOrigin
import com.cady.cadymarketing.domain.model.ReportSummary
import com.cady.cadymarketing.domain.model.ResultMetric
import com.cady.cadymarketing.domain.model.SavedContent

/**
 * Turns the orders that carry a campaign code, and the results written by hand, into one report per campaign. Pure Kotlin.
 *
 * Orders counted by a system and orders written by hand are added together: write by hand only what no system counts
 * (for example WhatsApp orders when Kady's invoices carry no campaign code), or the same order is counted twice.
 */
object ReportEngine {
    /** A date inside the period. With no period every order counts, even one without a date. */
    private fun inPeriod(date: String?, from: String?, to: String?): Boolean {
        if (from == null && to == null) return true
        if (date == null) return false
        return (from == null || date >= from) && (to == null || date <= to)
    }

    fun compute(
        campaigns: List<Campaign>,
        content: List<SavedContent>,
        orders: List<AttributedOrder>,
        results: List<ManualResult>,
        from: String? = null,
        to: String? = null,
    ): ReportSummary {
        val campaignByCode = campaigns.associateBy { OrderMapper.code(it.code).orEmpty() }
        val postsByCampaign = content.filter { it.campaignId != null }.groupBy { it.campaignId }
        val periodOrders = orders.filter { inPeriod(it.date, from, to) }
        val periodResults = results.filter { inPeriod(it.date, from, to) }

        val reports = campaigns.map { campaign ->
            val code = OrderMapper.code(campaign.code).orEmpty()
            val mine = periodOrders.filter { it.campaignCode == code }
            val counted = mine.filter { !it.cancelled }
            val manual = periodResults.filter { it.campaignId == campaign.id }
            val posts = postsByCampaign[campaign.id].orEmpty()

            // Per channel: the orders of the systems and the orders written by hand for that channel.
            val channelOrders = HashMap<Channel?, Int>()
            val channelRevenue = HashMap<Channel?, Long>()
            for (order in counted) {
                channelOrders[order.channel] = (channelOrders[order.channel] ?: 0) + 1
                channelRevenue[order.channel] = (channelRevenue[order.channel] ?: 0L) + (order.amount ?: 0L)
            }
            for (result in manual) {
                when (result.metric) {
                    ResultMetric.ORDERS ->
                        channelOrders[result.channel] = (channelOrders[result.channel] ?: 0) + result.value.toInt()
                    ResultMetric.SALES ->
                        channelRevenue[result.channel] = (channelRevenue[result.channel] ?: 0L) + result.value
                    else -> Unit
                }
            }
            val channels = (channelOrders.keys + channelRevenue.keys).distinct()
                .map { ChannelStat(it, channelOrders[it] ?: 0, channelRevenue[it] ?: 0L) }
                .sortedWith(compareByDescending<ChannelStat> { it.orders }.thenByDescending { it.revenue })

            CampaignReport(
                campaign = campaign,
                posts = posts.size,
                published = posts.count { it.status == ContentStatus.PUBLISHED },
                siteOrders = counted.count { it.origin == OrderOrigin.SITE },
                kadyOrders = counted.count { it.origin == OrderOrigin.KADY },
                autoRevenue = counted.sumOf { it.amount ?: 0L },
                manualOrders = manual.filter { it.metric == ResultMetric.ORDERS }.sumOf { it.value },
                manualSales = manual.filter { it.metric == ResultMetric.SALES }.sumOf { it.value },
                reach = manual.filter { it.metric == ResultMetric.REACH }.sumOf { it.value },
                replies = manual.filter { it.metric == ResultMetric.REPLIES }.sumOf { it.value },
                cancelled = mine.count { it.cancelled },
                byChannel = channels,
            )
        }.sortedWith(
            compareByDescending<CampaignReport> { it.revenue }
                .thenByDescending { it.orders }
                .thenBy { it.campaign.name },
        )

        val orphans = periodOrders.filter { it.campaignCode !in campaignByCode }
        val bestChannel = reports.flatMap { it.byChannel }
            .filter { it.channel != null }
            .groupBy { it.channel }
            .map { (channel, stats) -> ChannelStat(channel, stats.sumOf { it.orders }, stats.sumOf { it.revenue }) }
            .filter { it.orders > 0 }
            .maxWithOrNull(compareBy<ChannelStat> { it.orders }.thenBy { it.revenue })
            ?.channel

        return ReportSummary(
            campaigns = reports,
            orders = reports.sumOf { it.orders },
            revenue = reports.sumOf { it.revenue },
            cancelled = reports.sumOf { it.cancelled },
            orphanOrders = orphans.count { !it.cancelled },
            orphanCodes = orphans.map { it.campaignCode }.distinct().sorted(),
            bestChannel = bestChannel,
            publishedPosts = reports.sumOf { it.published },
        )
    }

    /** The first and last day of the last [days] days up to [today] (inclusive), as the period of a report. */
    fun lastDays(today: String, days: Int): Pair<String, String> = CalendarMath.addDays(today, -(days - 1)) to today
}
