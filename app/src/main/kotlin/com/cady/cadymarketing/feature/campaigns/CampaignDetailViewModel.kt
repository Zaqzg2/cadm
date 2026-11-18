package com.cady.cadymarketing.feature.campaigns

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.core.navigation.Routes
import com.cady.cadymarketing.core.qr.QrBitmap
import com.cady.cadymarketing.data.brand.BrandRepository
import com.cady.cadymarketing.data.campaign.CampaignRepository
import com.cady.cadymarketing.data.content.ContentRepository
import com.cady.cadymarketing.data.media.ImageExporter
import com.cady.cadymarketing.domain.engine.CampaignRules
import com.cady.cadymarketing.domain.engine.TrackingLink
import com.cady.cadymarketing.domain.model.Campaign
import com.cady.cadymarketing.domain.model.CampaignStatus
import com.cady.cadymarketing.domain.model.SavedContent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CampaignDetailState(
    val loaded: Boolean = false,
    val campaign: Campaign? = null,
    val posts: List<SavedContent> = emptyList(),
    val links: List<TrackingLink> = emptyList(),
    val qr: QrPreview? = null,
)

/** One-time things the screen must do. */
sealed interface CampaignDetailEvent {
    data class Message(val text: String) : CampaignDetailEvent

    data class Share(val uri: Uri) : CampaignDetailEvent

    /** The campaign was deleted: leave its page. */
    data object Deleted : CampaignDetailEvent
}

@HiltViewModel
class CampaignDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val campaigns: CampaignRepository,
    content: ContentRepository,
    private val brandRepository: BrandRepository,
    private val exporter: ImageExporter,
) : ViewModel() {

    private val id: String = savedStateHandle.get<String>(Routes.ARG_ID).orEmpty()
    private val qr = MutableStateFlow<QrPreview?>(null)

    val state: StateFlow<CampaignDetailState> = combine(
        campaigns.observe(id),
        content.observeByCampaign(id),
        brandRepository.observeBrand(),
        qr,
    ) { campaign, posts, brand, preview ->
        CampaignDetailState(
            loaded = true,
            campaign = campaign,
            posts = posts,
            links = campaign?.let { CampaignRules.trackingLinks(brand, it.code) }.orEmpty(),
            qr = preview,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CampaignDetailState())

    private val _events = MutableSharedFlow<CampaignDetailEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<CampaignDetailEvent> = _events.asSharedFlow()

    fun update(name: String, goal: String, startDate: String?, endDate: String?) {
        viewModelScope.launch {
            campaigns.update(id, name, goal, startDate, endDate)
            say("حُفظت الحملة")
        }
    }

    fun setStatus(status: CampaignStatus) {
        viewModelScope.launch {
            campaigns.setStatus(id, status)
            say("صارت الحملة «${status.label}»")
        }
    }

    fun delete() {
        viewModelScope.launch {
            campaigns.delete(id)
            _events.emit(CampaignDetailEvent.Deleted)
        }
    }

    /** Makes the QR card of one tracking link (off the main thread) and shows it. */
    fun showQr(link: TrackingLink) {
        val campaign = state.value.campaign ?: return
        viewModelScope.launch {
            val brand = brandRepository.observeBrand().first()
            val subtitle = link.channel.label + " · " + campaign.code
            val card = withContext(Dispatchers.Default) {
                QrBitmap.create(link.url)?.let { QrBitmap.card(it, brand.name + " — " + campaign.name, subtitle) }
            }
            if (card == null) {
                say("تعذّر إنشاء رمز QR لهذا الرابط")
            } else {
                qr.value = QrPreview(campaign.name + " · " + link.channel.label, link.url, card)
            }
        }
    }

    fun hideQr() {
        qr.value = null
    }

    fun saveQr() {
        val preview = qr.value ?: return
        viewModelScope.launch {
            try {
                say("حُفظت الصورة في " + exporter.saveToDownloads(preview.bitmap))
            } catch (e: IOException) {
                say(e.message ?: "تعذّر حفظ الصورة")
            }
        }
    }

    fun shareQr() {
        val preview = qr.value ?: return
        viewModelScope.launch {
            try {
                _events.emit(CampaignDetailEvent.Share(exporter.shareUri(preview.bitmap)))
            } catch (e: IOException) {
                say("تعذّر تجهيز الصورة للمشاركة")
            }
        }
    }

    private fun say(text: String) {
        _events.tryEmit(CampaignDetailEvent.Message(text))
    }
}
