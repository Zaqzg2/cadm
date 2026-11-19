package com.cady.cadymarketing.feature.audience

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.core.navigation.Routes
import com.cady.cadymarketing.data.audience.AudienceException
import com.cady.cadymarketing.data.audience.AudienceRepository
import com.cady.cadymarketing.data.audience.CustomerRefreshReport
import com.cady.cadymarketing.data.cloud.CloudErrors
import com.cady.cadymarketing.data.content.ContentRepository
import com.cady.cadymarketing.domain.engine.AudienceEngine
import com.cady.cadymarketing.domain.model.Consent
import com.cady.cadymarketing.domain.model.ConsentStatus
import com.cady.cadymarketing.domain.model.Customer
import com.cady.cadymarketing.domain.model.Segment
import com.cady.cadymarketing.domain.model.SegmentKind
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class AudienceTab(val label: String) {
    CUSTOMERS("العملاء"),
    SEGMENTS("الشرائح"),
}

enum class ConsentFilter(val label: String) {
    ALL("الكل"),
    GRANTED("موافقون"),
    NONE("بلا موافقة"),
    DECLINED("رافضون"),
    NO_PHONE("بلا رقم صالح"),
}

/** One customer with what is recorded about their consent. */
data class CustomerRow(val customer: Customer, val consent: Consent?) {
    val status: ConsentStatus get() = consent?.status ?: ConsentStatus.UNKNOWN
}

/** One segment with how many customers it covers and how many of them may be sent to. */
data class SegmentRow(val segment: Segment, val members: Int, val sendable: Int)

data class AudienceState(
    val loaded: Boolean = false,
    val tab: AudienceTab = AudienceTab.CUSTOMERS,
    val customers: List<Customer> = emptyList(),
    val consents: Map<String, Consent> = emptyMap(),
    val segments: List<Segment> = emptyList(),
    val filter: ConsentFilter = ConsentFilter.ALL,
    val query: String = "",
    val refreshing: Boolean = false,
    val lastRefresh: Long? = null,
    val report: CustomerRefreshReport? = null,
    val isPreview: Boolean = false,
    /** Set when the screen was opened from a post to send it: the name of that post. */
    val sendingPost: String? = null,
) {
    val rows: List<CustomerRow> get() = customers.map { CustomerRow(it, consents[it.id]) }

    val visible: List<CustomerRow>
        get() {
            val needle = query.trim()
            return rows.filter { row ->
                val matchesFilter = when (filter) {
                    ConsentFilter.ALL -> true
                    ConsentFilter.GRANTED -> row.status == ConsentStatus.GRANTED
                    ConsentFilter.NONE -> row.status == ConsentStatus.UNKNOWN
                    ConsentFilter.DECLINED -> row.status == ConsentStatus.DECLINED
                    ConsentFilter.NO_PHONE -> row.customer.phone == null
                }
                matchesFilter && (
                    needle.isEmpty() ||
                        row.customer.name.contains(needle, ignoreCase = true) ||
                        row.customer.area.contains(needle, ignoreCase = true) ||
                        row.customer.rawPhone.contains(needle)
                    )
            }
        }

    val segmentRows: List<SegmentRow>
        get() = segments.map { segment ->
            val members = AudienceEngine.members(segment, customers)
            SegmentRow(segment, members.size, AudienceEngine.partition(members, consents).eligible.size)
        }

    val grantedCount: Int get() = customers.count { consents[it.id]?.status == ConsentStatus.GRANTED }
    val declinedCount: Int get() = customers.count { consents[it.id]?.status == ConsentStatus.DECLINED }

    /** The reps' names as Kady has them, for choosing one in a segment. */
    val reps: List<String> get() = customers.map { it.repName.trim() }.filter { it.isNotEmpty() }.distinct().sorted()
}

@HiltViewModel
class AudienceViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val audience: AudienceRepository,
    private val content: ContentRepository,
) : ViewModel() {

    /** The post this screen was opened to send (from the library), or null. */
    val contentId: String? = savedStateHandle.get<String>(Routes.ARG_CONTENT_ID)

    private val _state = MutableStateFlow(
        AudienceState(
            isPreview = audience.isPreview,
            tab = if (contentId != null) AudienceTab.SEGMENTS else AudienceTab.CUSTOMERS,
        ),
    )
    val state: StateFlow<AudienceState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            audience.observeCustomers().collect { list -> _state.update { it.copy(customers = list, loaded = true) } }
        }
        viewModelScope.launch {
            audience.observeConsents().collect { map -> _state.update { it.copy(consents = map) } }
        }
        viewModelScope.launch {
            audience.observeSegments().collect { list -> _state.update { it.copy(segments = list) } }
        }
        viewModelScope.launch {
            audience.observeLastRefresh().collect { at -> _state.update { it.copy(lastRefresh = at) } }
        }
        if (contentId != null) {
            viewModelScope.launch {
                val post = content.get(contentId)
                _state.update { it.copy(sendingPost = post?.productName) }
            }
        }
    }

    fun selectTab(tab: AudienceTab) {
        _state.update { it.copy(tab = tab) }
    }

    fun setFilter(filter: ConsentFilter) {
        _state.update { it.copy(filter = filter) }
    }

    fun setQuery(query: String) {
        _state.update { it.copy(query = query) }
    }

    /** Reads Kady's customers again. The answer says what was read, and what could not be understood. */
    fun refresh() {
        if (_state.value.refreshing) return
        if (audience.isPreview) {
            say("وضع المعاينة: لا عملاء لأن Firebase غير مربوط في هذا البناء")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(refreshing = true) }
            try {
                val report = audience.refreshCustomers()
                _state.update { it.copy(report = report) }
                val skipped = if (report.skipped.isEmpty()) "" else " (تخطّيت ${report.skipped.size})"
                say("قُرئ ${report.customers} عميلًا من كادي$skipped")
            } catch (e: CancellationException) {
                throw e
            } catch (e: AudienceException) {
                say(e.message ?: "تعذّرت قراءة العملاء")
            } catch (e: Exception) {
                say(CloudErrors.message(e))
            } finally {
                _state.update { it.copy(refreshing = false) }
            }
        }
    }

    fun setConsent(customerId: String, status: ConsentStatus, note: String) {
        viewModelScope.launch {
            audience.setConsent(customerId, status, note)
            say(
                when (status) {
                    ConsentStatus.GRANTED -> "سُجّلت موافقة العميل"
                    ConsentStatus.DECLINED -> "سُجّل طلب العميل التوقف عن الرسائل"
                    ConsentStatus.UNKNOWN -> "أُزيل تسجيل الموافقة"
                },
            )
        }
    }

    fun saveSegment(id: String?, name: String, kind: SegmentKind, area: String, repName: String, memberIds: List<String>) {
        val problems = AudienceEngine.validate(name, kind, memberIds)
        if (problems.isNotEmpty()) return say(problems.first())
        viewModelScope.launch {
            val segment = audience.saveSegment(id, name, kind, area, repName, memberIds)
            say("حُفظت الشريحة «${segment.name}»")
        }
    }

    fun deleteSegment(id: String) {
        viewModelScope.launch {
            audience.deleteSegment(id)
            say("حُذفت الشريحة")
        }
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
    }
}
