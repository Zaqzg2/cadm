package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Campaign
import com.cady.cadymarketing.domain.model.CampaignPhase
import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.ProductRow
import com.cady.cadymarketing.domain.model.SavedContent

enum class AlertLevel { URGENT, NOTICE, INFO }

/** The screen an alert leads to when it is tapped. */
enum class AlertTarget { LIBRARY, CAMPAIGNS, PRODUCTS, STUDIO }

data class HomeAlert(
    val level: AlertLevel,
    val title: String,
    val detail: String,
    val target: AlertTarget,
    /** Set for an alert about one product: the Studio opens on it. */
    val productId: String? = null,
)

/** The numbers at the top of the home screen. */
data class HomeStats(
    val runningCampaigns: Int,
    val drafts: Int,
    val pending: Int,
    val approved: Int,
    val publishedThisMonth: Int,
    val scheduledNextWeek: Int,
)

data class HomeInsights(val stats: HomeStats, val alerts: List<HomeAlert>)

/** The indicators and the alerts of the home screen, worked out from what is stored. Pure Kotlin. */
object HomeInsightsEngine {
    private const val OFFER_WARNING_DAYS = 3
    private const val CAMPAIGN_WARNING_DAYS = 3
    private const val MAX_OFFER_ALERTS = 3
    private val waiting = setOf(ContentStatus.DRAFT, ContentStatus.PENDING, ContentStatus.APPROVED, ContentStatus.RETURNED)

    /** [nowMinute] is "yyyy-MM-ddTHH:mm", the same shape as a post's schedule, so the two compare as text. */
    fun compute(
        content: List<SavedContent>,
        campaigns: List<Campaign>,
        products: List<ProductRow>,
        today: String,
        nowMinute: String,
    ): HomeInsights {
        val month = today.take(7)
        val weekEnd = CalendarMath.addDays(today, 6)
        val stats = HomeStats(
            runningCampaigns = campaigns.count { it.phase(today) == CampaignPhase.RUNNING },
            drafts = content.count { it.status == ContentStatus.DRAFT },
            pending = content.count { it.status == ContentStatus.PENDING },
            approved = content.count { it.status == ContentStatus.APPROVED },
            publishedThisMonth = content.count {
                it.status == ContentStatus.PUBLISHED && DateUtil.fromMillis(it.updatedAt).startsWith(month)
            },
            scheduledNextWeek = content.count { post ->
                val date = post.scheduledDate
                post.status in waiting && date != null && date >= today && date <= weekEnd
            },
        )

        val alerts = ArrayList<HomeAlert>()

        val late = content.filter { post -> post.status in waiting && post.scheduledAt != null && post.scheduledAt < nowMinute }
        if (late.isNotEmpty()) {
            val first = late.minByOrNull { it.scheduledAt.orEmpty() }!!
            alerts.add(
                HomeAlert(
                    AlertLevel.URGENT,
                    if (late.size == 1) "منشور فات موعد نشره" else "${late.size} منشورات فات موعد نشرها",
                    "أقدمها: ${first.productName} (${first.scheduledAt.orEmpty().replace('T', ' ')})",
                    AlertTarget.LIBRARY,
                ),
            )
        }

        if (stats.pending > 0) {
            alerts.add(
                HomeAlert(
                    AlertLevel.NOTICE,
                    if (stats.pending == 1) "منشور بانتظار الاعتماد" else "${stats.pending} منشورات بانتظار الاعتماد",
                    "راجعها في المكتبة ثم اعتمدها أو أرجعها",
                    AlertTarget.LIBRARY,
                ),
            )
        }

        val endingOffers = products
            .filter { it.active && it.product.hasOffer && it.product.offerEnd.isNotEmpty() }
            .mapNotNull { row ->
                val left = CalendarMath.daysBetween(today, row.product.offerEnd)
                if (left != null && left in 0..OFFER_WARNING_DAYS) row to left else null
            }
            .sortedBy { it.second }
            .take(MAX_OFFER_ALERTS)
        for ((row, left) in endingOffers) {
            alerts.add(
                HomeAlert(
                    AlertLevel.NOTICE,
                    "ينتهي عرض ${row.product.name} " + dueText(left),
                    "اكتب منشورًا يذكّر العملاء قبل أن ينتهي",
                    AlertTarget.STUDIO,
                    productId = row.product.id,
                ),
            )
        }

        for (campaign in campaigns.filter { it.phase(today) == CampaignPhase.OVERDUE }) {
            alerts.add(
                HomeAlert(
                    AlertLevel.NOTICE,
                    "حملة «${campaign.name}» تجاوزت موعد نهايتها",
                    "أنهِها أو مدِّد تاريخ نهايتها",
                    AlertTarget.CAMPAIGNS,
                ),
            )
        }

        for (campaign in campaigns.filter { it.phase(today) == CampaignPhase.RUNNING && it.endDate != null }) {
            val left = CalendarMath.daysBetween(today, campaign.endDate!!) ?: continue
            if (left in 0..CAMPAIGN_WARNING_DAYS) {
                alerts.add(
                    HomeAlert(
                        AlertLevel.INFO,
                        "تنتهي حملة «${campaign.name}» " + dueText(left),
                        "جهّز آخر منشوراتها",
                        AlertTarget.CAMPAIGNS,
                    ),
                )
            }
        }

        val withoutPicture = products.count { it.active && it.product.picturePath == null }
        if (withoutPicture > 0) {
            alerts.add(
                HomeAlert(
                    AlertLevel.INFO,
                    if (withoutPicture == 1) "صنف بلا صورة مرتبطة" else "$withoutPicture أصناف بلا صورة مرتبطة",
                    "اربط صورة بكل صنف لتظهر في الملصقات تلقائيًا",
                    AlertTarget.PRODUCTS,
                ),
            )
        }

        return HomeInsights(stats, alerts.sortedBy { it.level.ordinal })
    }

    private fun dueText(daysLeft: Int): String = when (daysLeft) {
        0 -> "اليوم"
        1 -> "غدًا"
        2 -> "بعد يومين"
        else -> "بعد $daysLeft أيام"
    }
}
