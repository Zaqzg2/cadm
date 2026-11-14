package com.cady.cadymarketing.feature.studio

import android.graphics.Bitmap
import android.net.Uri
import com.cady.cadymarketing.domain.engine.DefaultVocabulary
import com.cady.cadymarketing.domain.engine.GatewayConfig
import com.cady.cadymarketing.domain.engine.IdeaBank
import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.CheckResult
import com.cady.cadymarketing.domain.model.Formula
import com.cady.cadymarketing.domain.model.Product
import com.cady.cadymarketing.domain.model.SavedContent

/** The six tools of the bottom bar. At most one panel is open at a time, and it never hides the preview. */
enum class StudioTool { PRODUCT, CHANNEL, TEXT, DESIGN, PICTURE, SCHEDULE }

/** Where a poster is shared to. A null package opens the system share sheet. */
enum class ShareTarget(val packageName: String?) {
    WHATSAPP("com.whatsapp"),
    WHATSAPP_BUSINESS("com.whatsapp.w4b"),
    OTHER(null),
}

/** Everything the Studio screen shows, in one immutable value. */
data class StudioState(
    val loading: Boolean = true,
    val products: List<Product> = emptyList(),
    val productId: String? = null,
    val brand: Brand = Brand(),
    val channel: Channel = Channel.WHATSAPP,
    val tones: List<String> = DefaultVocabulary.tones.keys.toList(),
    val tone: String = DefaultVocabulary.DEFAULT_TONE,
    val angles: List<String> = IdeaBank.angles,
    val angle: String = IdeaBank.angles.first(),
    val formulas: List<Formula> = emptyList(),
    val formulaIndex: Int = 0,
    val designId: Int = 0,
    val pictureFile: String? = null,
    /** "yyyy-MM-ddTHH:mm", or null when no schedule is set. */
    val scheduledAt: String? = null,
    val openTool: StudioTool? = null,
    val ideas: List<String> = emptyList(),
    val check: CheckResult = CheckResult.Empty,
    val preview: Bitmap? = null,
    val thumbs: Map<Int, Bitmap> = emptyMap(),
    val aiBusy: Boolean = false,
    val gateway: GatewayConfig = GatewayConfig(),
    val gatewayTesting: Boolean = false,
    val gatewayTestMessage: String? = null,
    val editingId: String? = null,
    val saved: List<SavedContent> = emptyList(),
    /** True when there is work that a close would lose. */
    val dirty: Boolean = false,
) {
    val product: Product? get() = products.firstOrNull { it.id == productId }

    val currentText: String get() = formulas.getOrNull(formulaIndex)?.text.orEmpty()

    /** The approval gate: there is a text and no blocking error (warnings never block). */
    val canApprove: Boolean get() = product != null && currentText.isNotBlank() && check.passed
}

/** One-time things the screen must do. */
sealed interface StudioEvent {
    data class Message(val text: String) : StudioEvent

    data class CopyText(val text: String) : StudioEvent

    data class Share(val uri: Uri, val text: String, val packageName: String?) : StudioEvent
}
