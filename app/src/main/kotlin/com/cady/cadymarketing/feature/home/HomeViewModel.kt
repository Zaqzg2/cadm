package com.cady.cadymarketing.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.brand.BrandRepository
import com.cady.cadymarketing.data.campaign.CampaignRepository
import com.cady.cadymarketing.data.catalog.CatalogRepository
import com.cady.cadymarketing.data.content.ContentRepository
import com.cady.cadymarketing.data.inventory.InventoryRepository
import com.cady.cadymarketing.data.vocab.VocabularyRepository
import com.cady.cadymarketing.domain.engine.DailyIdea
import com.cady.cadymarketing.domain.engine.DailyIdeas
import com.cady.cadymarketing.domain.engine.DateUtil
import com.cady.cadymarketing.domain.engine.HomeAlert
import com.cady.cadymarketing.domain.engine.HomeInsightsEngine
import com.cady.cadymarketing.domain.engine.HomeStats
import com.cady.cadymarketing.domain.engine.InventoryInsights
import com.cady.cadymarketing.domain.engine.InventoryRules
import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Product
import com.cady.cadymarketing.domain.model.SavedContent
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class HomeState(
    val loaded: Boolean = false,
    val today: String = "",
    val stats: HomeStats = HomeStats(0, 0, 0, 0, 0, 0),
    val alerts: List<HomeAlert> = emptyList(),
    val idea: DailyIdea? = null,
    val hasProducts: Boolean = false,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    content: ContentRepository,
    campaigns: CampaignRepository,
    catalog: CatalogRepository,
    brand: BrandRepository,
    vocabulary: VocabularyRepository,
    inventory: InventoryRepository,
) : ViewModel() {

    private val core = combine(
        content.observeAll(),
        campaigns.observeAll(),
        catalog.observeRows(),
        brand.observeBrand(),
        vocabulary.observeMerged(),
    ) { posts, campaignList, rows, currentBrand, vocab ->
        val today = DateUtil.today()
        val insights = HomeInsightsEngine.compute(posts, campaignList, rows, today, DateUtil.nowMinute())
        // The same list the Studio writes about (active products), so both agree on the idea of the day.
        val products = rows.filter { it.active }.map { it.product }
        Core(posts, insights.stats, insights.alerts, today, products, vocab.ideas, currentBrand)
    }

    val state: StateFlow<HomeState> = combine(core, inventory.observeStock()) { base, stock ->
        // Stock adds alerts (a product that ran out while a post is ready, a surplus nobody writes about, an old snapshot)
        // and leans the idea of the day toward products in surplus (the Studio applies the same lean).
        val stockAlerts = InventoryInsights.alerts(stock.byProduct, base.products, base.posts, System.currentTimeMillis(), stock.ageDays)
        HomeState(
            loaded = true,
            today = base.today,
            stats = base.stats,
            alerts = (base.alerts + stockAlerts).sortedBy { it.level.ordinal },
            idea = DailyIdeas.pick(base.today, base.products, base.ideas, base.brand, InventoryRules.preferredForIdeas(stock.byProduct)),
            hasProducts = base.products.isNotEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeState())

    /** What the five content flows give, before the stock is added. */
    private class Core(
        val posts: List<SavedContent>,
        val stats: HomeStats,
        val alerts: List<HomeAlert>,
        val today: String,
        val products: List<Product>,
        val ideas: Map<String, List<String>>,
        val brand: Brand,
    )
}
