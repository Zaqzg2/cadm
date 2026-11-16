package com.cady.cadymarketing.feature.identity

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.core.poster.PosterRenderer
import com.cady.cadymarketing.data.brand.BrandRepository
import com.cady.cadymarketing.data.media.PictureStore
import com.cady.cadymarketing.data.product.ProductRepository
import com.cady.cadymarketing.data.product.SampleCatalog
import com.cady.cadymarketing.domain.engine.BrandRules
import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.Product
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The draft of the identity form. Every field is the text as typed, so a half-typed colour never gets lost. */
data class IdentityState(
    val loaded: Boolean = false,
    val name: String = "",
    val primaryHex: String = "",
    val darkHex: String = "",
    val accentHex: String = "",
    val tone: String = "",
    val banned: String = "",
    val whatsapp: String = "",
    val siteUrl: String = "",
    val hashtags: String = "",
    val logoFile: String? = null,
    val designId: Int = 0,
    val preview: Bitmap? = null,
    val dirty: Boolean = false,
) {
    fun toBrand(): Brand = Brand(
        name = name,
        primaryHex = primaryHex,
        darkHex = darkHex,
        accentHex = accentHex,
        tone = tone,
        bannedPhrases = banned,
        whatsapp = whatsapp,
        siteUrl = siteUrl,
        hashtags = BrandRules.parseHashtags(hashtags),
        logoFile = logoFile,
    )

    val problems: List<String> get() = BrandRules.validate(toBrand())

    val canSave: Boolean get() = dirty && problems.isEmpty()
}

@HiltViewModel
class IdentityViewModel @Inject constructor(
    private val brandRepository: BrandRepository,
    private val productRepository: ProductRepository,
    private val pictures: PictureStore,
    private val renderer: PosterRenderer,
) : ViewModel() {

    private val _state = MutableStateFlow(IdentityState())
    val state: StateFlow<IdentityState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    /** The logo that is really saved (a logo picked but never saved is deleted when it is replaced). */
    private var savedLogo: String? = null
    private var sample: Product = SampleCatalog.products.first()
    private var previewJob: Job? = null

    init {
        viewModelScope.launch {
            val brand = brandRepository.observeBrand().first()
            productRepository.observeProducts().first().firstOrNull()?.let { sample = it }
            savedLogo = brand.logoFile
            _state.value = fromBrand(brand, loaded = true)
            requestPreview()
        }
    }

    private fun fromBrand(brand: Brand, loaded: Boolean, designId: Int = _state.value.designId) = IdentityState(
        loaded = loaded,
        name = brand.name,
        primaryHex = brand.primaryHex,
        darkHex = brand.darkHex,
        accentHex = brand.accentHex,
        tone = brand.tone,
        banned = brand.bannedPhrases,
        whatsapp = brand.whatsapp,
        siteUrl = brand.siteUrl,
        hashtags = BrandRules.formatHashtags(brand.hashtags),
        logoFile = brand.logoFile,
        designId = designId,
    )

    private fun edit(change: (IdentityState) -> IdentityState) {
        _state.update { change(it).copy(dirty = true) }
        requestPreview()
    }

    fun onName(value: String) = edit { it.copy(name = value) }

    fun onPrimary(value: String) = edit { it.copy(primaryHex = value) }

    fun onDark(value: String) = edit { it.copy(darkHex = value) }

    fun onAccent(value: String) = edit { it.copy(accentHex = value) }

    fun onTone(value: String) = edit { it.copy(tone = value) }

    fun onBanned(value: String) = edit { it.copy(banned = value) }

    fun onWhatsapp(value: String) = edit { it.copy(whatsapp = value) }

    fun onSite(value: String) = edit { it.copy(siteUrl = value) }

    fun onHashtags(value: String) = edit { it.copy(hashtags = value) }

    fun onDesign(id: Int) {
        _state.update { it.copy(designId = id) }
        requestPreview()
    }

    /** Fills the dark colour with a deep shade of the main colour. */
    fun darkFromPrimary() {
        val dark = BrandRules.darken(_state.value.primaryHex)
        if (dark == null) say("اكتب اللون الأساسي أولًا") else onDark(dark)
    }

    fun pickLogo(uri: Uri) {
        viewModelScope.launch {
            val file = pictures.importLogo(uri)
            if (file == null) {
                say("تعذّر قراءة الشعار، جرّب صورة أخرى")
                return@launch
            }
            dropDraftLogo()
            edit { it.copy(logoFile = file) }
        }
    }

    fun removeLogo() {
        dropDraftLogo()
        edit { it.copy(logoFile = null) }
    }

    /** Every field back to the original tool's values (the logo too). Nothing is saved until "حفظ". */
    fun useDefaults() {
        dropDraftLogo()
        _state.update { fromBrand(Brand(), loaded = true).copy(dirty = true) }
        requestPreview()
    }

    fun save() {
        val draft = BrandRules.clean(_state.value.toBrand())
        val problems = BrandRules.validate(draft)
        if (problems.isNotEmpty()) {
            say(problems.first())
            return
        }
        viewModelScope.launch {
            brandRepository.save(draft)
            val old = savedLogo
            if (old != null && old != draft.logoFile) pictures.delete(old)
            savedLogo = draft.logoFile
            _state.value = fromBrand(draft, loaded = true)
            requestPreview()
            say("حُفظت الهوية")
        }
    }

    suspend fun loadThumb(fileName: String): Bitmap? = withContext(Dispatchers.IO) { pictures.load(fileName, LOGO_THUMB) }

    /** A logo that was picked in this session but never saved is of no use once it is replaced. */
    private fun dropDraftLogo() {
        val draft = _state.value.logoFile
        if (draft != null && draft != savedLogo) pictures.delete(draft)
    }

    private fun requestPreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            val s = _state.value
            val brand = BrandRules.clean(s.toBrand())
            val product = sample
            val bitmap = withContext(Dispatchers.Default) {
                renderer.render(product, brand, Channel.WHATSAPP, s.designId, product.picturePath, PREVIEW_WIDTH)
            }
            _state.update { it.copy(preview = bitmap) }
        }
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
    }

    private companion object {
        const val PREVIEW_WIDTH = 420
        const val PREVIEW_DEBOUNCE_MS = 200L
        const val LOGO_THUMB = 400
    }
}
