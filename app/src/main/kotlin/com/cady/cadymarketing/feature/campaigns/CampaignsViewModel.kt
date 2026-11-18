package com.cady.cadymarketing.feature.campaigns

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.campaign.CampaignRepository
import com.cady.cadymarketing.data.content.ContentRepository
import com.cady.cadymarketing.domain.engine.DateUtil
import com.cady.cadymarketing.domain.model.Campaign
import com.cady.cadymarketing.domain.model.CampaignPhase
import com.cady.cadymarketing.domain.model.ContentStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class CampaignFilter(val label: String) {
    ACTIVE("النشطة"),
    ENDED("المنتهية"),
    ALL("الكل"),
}

/** One line of the campaigns list: the campaign, where it stands today, and how many posts it has. */
data class CampaignItem(
    val campaign: Campaign,
    val phase: CampaignPhase,
    val posts: Int,
    val ready: Int,
)

data class CampaignsState(
    val loaded: Boolean = false,
    val items: List<CampaignItem> = emptyList(),
    val filter: CampaignFilter = CampaignFilter.ACTIVE,
) {
    val visible: List<CampaignItem>
        get() = when (filter) {
            CampaignFilter.ACTIVE -> items.filter { it.phase != CampaignPhase.ENDED }
            CampaignFilter.ENDED -> items.filter { it.phase == CampaignPhase.ENDED }
            CampaignFilter.ALL -> items
        }
}

@HiltViewModel
class CampaignsViewModel @Inject constructor(
    private val campaigns: CampaignRepository,
    content: ContentRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(CampaignFilter.ACTIVE)

    val state: StateFlow<CampaignsState> = combine(campaigns.observeAll(), content.observeAll(), filter) { list, posts, selected ->
        val today = DateUtil.today()
        val byCampaign = posts.filter { it.campaignId != null }.groupBy { it.campaignId }
        CampaignsState(
            loaded = true,
            items = list.map { campaign ->
                val mine = byCampaign[campaign.id].orEmpty()
                CampaignItem(
                    campaign = campaign,
                    phase = campaign.phase(today),
                    posts = mine.size,
                    ready = mine.count { it.status == ContentStatus.APPROVED || it.status == ContentStatus.PUBLISHED },
                )
            },
            filter = selected,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CampaignsState())

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    fun setFilter(value: CampaignFilter) {
        filter.update { value }
    }

    fun create(name: String, goal: String, startDate: String?, endDate: String?) {
        viewModelScope.launch {
            val campaign = campaigns.create(name, goal, startDate, endDate)
            _messages.tryEmit("أُنشئت الحملة «${campaign.name}» برمز ${campaign.code}")
        }
    }
}
