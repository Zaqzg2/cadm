package com.cady.cadymarketing.feature.audience

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.core.navigation.Routes
import com.cady.cadymarketing.data.audience.AudienceRepository
import com.cady.cadymarketing.data.content.ContentRepository
import com.cady.cadymarketing.domain.engine.AudienceEngine
import com.cady.cadymarketing.domain.engine.SendProgress
import com.cady.cadymarketing.domain.model.Consent
import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.Customer
import com.cady.cadymarketing.domain.model.Exclusion
import com.cady.cadymarketing.domain.model.SavedContent
import com.cady.cadymarketing.domain.model.Segment
import com.cady.cadymarketing.domain.model.SendStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** One customer who may be sent to, and what was already done for them in this list. */
data class SendRecipient(val customer: Customer, val status: SendStatus?)

data class SendListState(
    val loaded: Boolean = false,
    val segment: Segment? = null,
    /** The posts that may be sent: approved ones and ones already published. */
    val sendable: List<SavedContent> = emptyList(),
    val post: SavedContent? = null,
    val recipients: List<SendRecipient> = emptyList(),
    val excluded: List<Pair<Customer, Exclusion>> = emptyList(),
    val progress: SendProgress = SendProgress(0, 0),
) {
    /** The first customer nobody has handled yet. */
    val next: SendRecipient? get() = recipients.firstOrNull { it.status == null }

    val overLimit: Boolean get() = recipients.size > AudienceEngine.SOFT_DAILY_LIMIT

    /** True when the post names the customer ({اسم العميل}); otherwise every customer gets the very same message. */
    val hasName: Boolean get() = post?.let { AudienceEngine.hasNamePlaceholder(it.text) } == true

    /** The text as the next customer (or the first, when all are done) will receive it. */
    val previewText: String?
        get() {
            val current = post ?: return null
            val customer = (next ?: recipients.firstOrNull())?.customer ?: return current.text
            return AudienceEngine.personalize(current.text, customer)
        }
}

/** One-time things the screen must do. */
sealed interface SendListEvent {
    data class Message(val text: String) : SendListEvent

    /** Open this WhatsApp chat link. */
    data class OpenChat(val url: String) : SendListEvent

    data class CopyText(val text: String) : SendListEvent
}

@HiltViewModel
class SendListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val audience: AudienceRepository,
    content: ContentRepository,
) : ViewModel() {

    private val segmentId: String = savedStateHandle.get<String>(Routes.ARG_SEGMENT_ID).orEmpty()
    private val selectedPost = MutableStateFlow(savedStateHandle.get<String>(Routes.ARG_CONTENT_ID))

    private var segments: List<Segment> = emptyList()
    private var customers: List<Customer> = emptyList()
    private var consents: Map<String, Consent> = emptyMap()
    private var marks: Map<String, SendStatus> = emptyMap()
    private var posts: List<SavedContent> = emptyList()
    private var loaded = false

    private val _state = MutableStateFlow(SendListState())
    val state: StateFlow<SendListState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<SendListEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<SendListEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            audience.observeSegments().collect {
                segments = it
                loaded = true
                recompute()
            }
        }
        viewModelScope.launch {
            audience.observeCustomers().collect {
                customers = it
                recompute()
            }
        }
        viewModelScope.launch {
            audience.observeConsents().collect {
                consents = it
                recompute()
            }
        }
        viewModelScope.launch {
            content.observeAll().collect {
                posts = it
                recompute()
            }
        }
        viewModelScope.launch {
            selectedPost.collectLatest { id ->
                if (id == null) {
                    marks = emptyMap()
                    recompute()
                } else {
                    audience.observeMarks(id).collect {
                        marks = it
                        recompute()
                    }
                }
            }
        }
    }

    fun selectPost(id: String) {
        selectedPost.value = id
        recompute()
    }

    /** Opens the customer's chat with their own text, and counts them as sent (they can be undone if it was not). */
    fun send(customerId: String) {
        val current = _state.value
        val post = current.post ?: return say("اختر المنشور أولًا")
        val customer = current.recipients.firstOrNull { it.customer.id == customerId }?.customer ?: return
        val link = AudienceEngine.chatLink(post.text, customer) ?: return say("لا رقم صالح لهذا العميل")
        viewModelScope.launch {
            audience.setMark(post.id, customer.id, SendStatus.SENT)
            _events.emit(SendListEvent.OpenChat(link))
        }
    }

    fun sendNext() {
        val next = _state.value.next ?: return say("انتهت القائمة: لم يبقَ عميل")
        send(next.customer.id)
    }

    fun skip(customerId: String) = mark(customerId, SendStatus.SKIPPED)

    fun undo(customerId: String) = mark(customerId, null)

    fun copyText() {
        val text = _state.value.previewText ?: return say("اختر المنشور أولًا")
        _events.tryEmit(SendListEvent.CopyText(text))
    }

    private fun mark(customerId: String, status: SendStatus?) {
        val post = _state.value.post ?: return
        viewModelScope.launch { audience.setMark(post.id, customerId, status) }
    }

    private fun recompute() {
        val segment = segments.firstOrNull { it.id == segmentId }
        val post = posts.firstOrNull { it.id == selectedPost.value }
        val members = segment?.let { AudienceEngine.members(it, customers) }.orEmpty()
        val partition = AudienceEngine.partition(members, consents)
        _state.value = SendListState(
            loaded = loaded,
            segment = segment,
            sendable = posts.filter { it.status == ContentStatus.APPROVED || it.status == ContentStatus.PUBLISHED },
            post = post,
            recipients = partition.eligible.map { SendRecipient(it, marks[it.id]) },
            excluded = partition.excluded,
            progress = AudienceEngine.progress(partition.eligible, marks),
        )
    }

    private fun say(text: String) {
        _events.tryEmit(SendListEvent.Message(text))
    }
}
