package com.cady.cadymarketing.feature.studio

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.core.poster.PosterRenderer
import com.cady.cadymarketing.data.brand.BrandRepository
import com.cady.cadymarketing.data.campaign.CampaignRepository
import com.cady.cadymarketing.data.content.ContentRepository
import com.cady.cadymarketing.data.media.ImageExporter
import com.cady.cadymarketing.data.media.PictureStore
import com.cady.cadymarketing.data.product.ProductRepository
import com.cady.cadymarketing.data.settings.SettingsStore
import com.cady.cadymarketing.data.settings.StudioPrefs
import com.cady.cadymarketing.data.vocab.VocabularyRepository
import com.cady.cadymarketing.domain.engine.CalendarMath
import com.cady.cadymarketing.domain.engine.ClaimChecker
import com.cady.cadymarketing.domain.engine.CopyGenerator
import com.cady.cadymarketing.domain.engine.DailyIdeas
import com.cady.cadymarketing.domain.engine.DateUtil
import com.cady.cadymarketing.domain.engine.DefaultVocabulary
import com.cady.cadymarketing.domain.engine.GatewayClient
import com.cady.cadymarketing.domain.engine.GatewayConfig
import com.cady.cadymarketing.domain.engine.GatewayException
import com.cady.cadymarketing.domain.engine.GenerationRequest
import com.cady.cadymarketing.domain.engine.IdeaBank
import com.cady.cadymarketing.domain.engine.LinkBuilder
import com.cady.cadymarketing.domain.engine.MergedVocabulary
import com.cady.cadymarketing.domain.engine.Placeholders
import com.cady.cadymarketing.domain.engine.VocabularyEdits
import com.cady.cadymarketing.domain.engine.VocabularyMerge
import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Campaign
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.CheckResult
import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.Formula
import com.cady.cadymarketing.domain.model.Issue
import com.cady.cadymarketing.domain.model.Product
import com.cady.cadymarketing.domain.model.SavedContent
import dagger.hilt.android.lifecycle.HiltViewModel
import java.io.IOException
import java.util.Locale
import java.util.UUID
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
import kotlin.random.Random

@HiltViewModel
class StudioViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val brandRepository: BrandRepository,
    private val contentRepository: ContentRepository,
    private val settings: SettingsStore,
    private val gateway: GatewayClient,
    private val pictures: PictureStore,
    private val exporter: ImageExporter,
    private val renderer: PosterRenderer,
    private val vocabularyRepository: VocabularyRepository,
    private val campaignRepository: CampaignRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _state = MutableStateFlow(StudioState())
    val state: StateFlow<StudioState> = _state.asStateFlow()

    private val _events = MutableSharedFlow<StudioEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<StudioEvent> = _events.asSharedFlow()

    /** The vocabulary in force: the built-in bank with the manager's edits (read once when the Studio opens). */
    private var vocab: MergedVocabulary = VocabularyMerge.merge(VocabularyEdits())

    /** Phrases used recently, oldest first (see PhraseMemory). */
    private var used: List<String> = emptyList()
    private var firstGenerationDone = false

    /** True after the person typed in the text field, so a regeneration does not silently drop the edit. */
    private var textEdited = false
    private var previewJob: Job? = null
    private var thumbsJob: Job? = null

    /** What the screen that opened the Studio asked it to start on; applied once the products and campaigns are in. */
    private val request: StudioLaunch = StudioLaunch.from(savedStateHandle)
    private var requestApplied = request.isEmpty
    private var productsReady = false
    private var campaignsReady = false

    init {
        viewModelScope.launch {
            val prefs = settings.studioPrefs.first()
            used = settings.usedPhrases.first()
            vocab = vocabularyRepository.observeMerged().first()
            val tones = vocab.tones.keys.toList()
            val angles = vocab.ideas.keys.toList()
            val defaultTone = if (DefaultVocabulary.DEFAULT_TONE in tones) DefaultVocabulary.DEFAULT_TONE else tones.first()
            _state.update { s ->
                s.copy(
                    channel = Channel.fromCode(prefs.channel),
                    tones = tones,
                    angles = angles,
                    tone = prefs.tone?.takeIf { it in tones } ?: defaultTone,
                    angle = prefs.angle?.takeIf { it in angles } ?: angles.first(),
                    designId = prefs.designId?.takeIf { id -> renderer.designs.any { it.id == id } } ?: s.designId,
                )
            }
            launch { brandRepository.observeBrand().collect { brand -> onBrand(brand) } }
            launch { productRepository.observeProducts().collect { list -> onProducts(list) } }
            launch { campaignRepository.observeAll().collect { list -> onCampaigns(list) } }
            launch { contentRepository.observeRecent().collect { list -> _state.update { it.copy(saved = list) } } }
            launch { settings.gatewayConfig.collect { config -> _state.update { it.copy(gateway = config) } } }
        }
    }

    // ------------------------------------------------------------------ inputs from repositories

    private fun onBrand(brand: Brand) {
        _state.update { it.copy(brand = brand) }
        recheck()
        refreshVisuals()
    }

    private fun onProducts(list: List<Product>) {
        val current = _state.value.productId
        val kept = current?.takeIf { id -> list.any { it.id == id } }
        val chosen = kept ?: list.firstOrNull()?.id
        _state.update { it.copy(products = list, productId = chosen, loading = false) }
        if (chosen == null) return
        if (!firstGenerationDone || kept == null) {
            firstGenerationDone = true
            regenerate()
        } else {
            recheck()
        }
        refreshVisuals()
        productsReady = true
        applyRequestIfReady()
    }

    private fun onCampaigns(list: List<Campaign>) {
        _state.update { s -> s.copy(campaigns = list, campaignId = s.campaignId?.takeIf { id -> list.any { it.id == id } }) }
        campaignsReady = true
        applyRequestIfReady()
    }

    /** Starts the Studio on what the opening screen asked for, once (a saved post, a campaign, a day, a product, the idea). */
    private fun applyRequestIfReady() {
        if (requestApplied || !productsReady || !campaignsReady) return
        requestApplied = true
        viewModelScope.launch {
            val contentId = request.contentId
            if (contentId != null) {
                val content = contentRepository.get(contentId)
                if (content != null) {
                    loadSaved(content)
                    return@launch
                }
                note("المحتوى المطلوب لم يعد موجودًا")
            }
            request.productId?.let { id -> if (_state.value.products.any { it.id == id }) selectProduct(id) }
            request.campaignId?.let { id -> if (_state.value.campaigns.any { it.id == id }) selectCampaign(id) }
            request.date?.let { setScheduleDate(it) }
            if (request.ideaOfDay) applyIdeaOfDay()
            // Nothing here was typed by the person, so closing right away must not ask to discard anything.
            _state.update { it.copy(dirty = false) }
        }
    }

    /** Opens on the idea of the day, written as a post (the same pick the home screen shows). */
    private fun applyIdeaOfDay() {
        val today = DateUtil.today()
        val pick = DailyIdeas.pick(today, _state.value.products, vocab.ideas, _state.value.brand) ?: return
        selectProduct(pick.product.id)
        val s = _state.value
        val product = s.product ?: return
        val seed = CalendarMath.dayNumber(today) ?: return
        val ideas = IdeaBank.local(pick.angle, product, s.brand, s.channel, s.campaign?.code, emptyList(), Random(seed), 1, vocab.ideas).ideas
        _state.update { it.copy(angle = if (pick.angle in it.angles) pick.angle else it.angle, ideas = ideas) }
        useIdea(0)
    }

    // ------------------------------------------------------------------ choices

    fun toggleTool(tool: StudioTool) {
        _state.update { it.copy(openTool = if (it.openTool == tool) null else tool) }
        if (_state.value.openTool == StudioTool.DESIGN) requestThumbs()
    }

    fun selectProduct(id: String) {
        _state.update { it.copy(productId = id) }
        regenerate()
        refreshVisuals()
    }

    fun selectChannel(channel: Channel) {
        _state.update { it.copy(channel = channel) }
        persistPrefs()
        regenerate()
        refreshVisuals()
    }

    fun selectTone(tone: String) {
        _state.update { it.copy(tone = tone) }
        persistPrefs()
        regenerate()
    }

    fun selectAngle(angle: String) {
        _state.update { it.copy(angle = angle, ideas = emptyList()) }
        persistPrefs()
    }

    fun selectFormula(index: Int) {
        _state.update { it.copy(formulaIndex = index) }
        recheck()
    }

    fun selectDesign(id: Int) {
        _state.update { it.copy(designId = id, dirty = true) }
        persistPrefs()
        requestPreview()
    }

    // ------------------------------------------------------------------ text

    /** Rebuilds the formulas for the current product, channel and tone (the "generate" button too). */
    fun regenerate() {
        val s = _state.value
        val product = s.product ?: return
        val result = CopyGenerator.generate(
            GenerationRequest(product, s.brand, s.channel, s.tone, s.campaign?.code, vocab.tones, used),
        )
        used = result.used
        persistUsed()
        val fresh = listOf(CopyGenerator.direct(product, s.brand, s.channel, s.campaign?.code)) +
            result.formulas +
            vocab.templates.map { CopyGenerator.fromTemplate(it, product, s.brand, s.channel, s.campaign?.code) }
        val keptEdit = if (textEdited && s.currentText.isNotBlank()) Formula(EDITED_FORMULA, s.currentText) else null
        _state.update {
            it.copy(
                formulas = if (keptEdit != null) fresh + keptEdit else fresh,
                formulaIndex = 0,
                ideas = emptyList(),
            )
        }
        textEdited = false
        recheck()
        if (keptEdit != null) note("حُفظ تعديلك السابق في صيغة «$EDITED_FORMULA»")
    }

    fun onTextChange(value: String) {
        textEdited = true
        _state.update { s ->
            if (s.formulas.isEmpty()) {
                s
            } else {
                s.copy(
                    formulas = s.formulas.mapIndexed { i, f -> if (i == s.formulaIndex) f.copy(text = value) else f },
                    dirty = true,
                )
            }
        }
        recheck()
    }

    fun addCustomerName() {
        val text = _state.value.currentText
        if (text.contains(Placeholders.CUSTOMER_NAME)) return
        onTextChange("أهلًا " + Placeholders.CUSTOMER_NAME + " 👋\n" + text)
    }

    fun fixIssue(issue: Issue) {
        onTextChange(ClaimChecker.fix(_state.value.currentText, issue))
    }

    fun copyText() {
        val text = _state.value.currentText
        if (text.isBlank()) return note("لا يوجد نص لنسخه")
        _events.tryEmit(StudioEvent.CopyText(text))
    }

    private fun recheck() {
        _state.update { s ->
            val product = s.product
            s.copy(
                check = if (product == null) {
                    CheckResult.Empty
                } else {
                    ClaimChecker.check(s.currentText, product, s.brand, s.channel)
                },
            )
        }
    }

    // ------------------------------------------------------------------ ideas and AI

    fun requestIdeas() {
        val s = _state.value
        val product = s.product ?: return
        viewModelScope.launch {
            if (s.gateway.isConfigured && !s.aiBusy) {
                _state.update { it.copy(aiBusy = true) }
                try {
                    val list = gateway.generateIdeas(s.gateway, product, s.brand, s.angle)
                    if (list.isNotEmpty()) {
                        _state.update { it.copy(ideas = list) }
                        return@launch
                    }
                } catch (e: GatewayException) {
                    note("الذكاء غير متاح (${e.message})، هذه أفكار جاهزة")
                } finally {
                    _state.update { it.copy(aiBusy = false) }
                }
            }
            localIdeas()
        }
    }

    private fun localIdeas() {
        val s = _state.value
        val product = s.product ?: return
        val result = IdeaBank.local(s.angle, product, s.brand, s.channel, s.campaign?.code, used, bank = vocab.ideas)
        used = result.used
        persistUsed()
        _state.update { it.copy(ideas = result.ideas) }
    }

    /** "Write a post": the idea, a price line when the angle calls for one, and a call to order. */
    fun useIdea(index: Int) {
        val s = _state.value
        val product = s.product ?: return
        val idea = s.ideas.getOrNull(index) ?: return
        val toneVocabulary = vocab.tones[s.tone] ?: vocab.tones.values.first()
        val text = IdeaBank.toPost(idea, s.angle, toneVocabulary, product, s.brand, s.channel, s.campaign?.code, withPriceLine = vocab.priceAngles)
        _state.update { st ->
            st.copy(
                formulas = listOf(Formula(IDEA_FORMULA, text)) + st.formulas.filter { it.name != IDEA_FORMULA },
                formulaIndex = 0,
                dirty = true,
            )
        }
        recheck()
    }

    fun generateWithAi() {
        val s = _state.value
        val product = s.product ?: return
        if (s.aiBusy) return
        viewModelScope.launch {
            _state.update { it.copy(aiBusy = true) }
            try {
                val posts = gateway.generatePosts(s.gateway, product, s.brand, s.tone)
                val added = posts.mapIndexed { i, post ->
                    val text = if (post.contains("{الرابط}")) post else post + "\n{الرابط}"
                    Formula("ذكاء ${i + 1}", Placeholders.fill(text, product, s.brand, s.channel, s.campaign?.code))
                }
                _state.update { st ->
                    st.copy(formulas = st.formulas + added, formulaIndex = st.formulas.size, dirty = true)
                }
                recheck()
                note("أُضيفت ${added.size} صيغ بالذكاء")
            } catch (e: GatewayException) {
                note("تعذّر: " + e.message)
            } finally {
                _state.update { it.copy(aiBusy = false) }
            }
        }
    }

    fun saveGateway(url: String, token: String) {
        viewModelScope.launch {
            settings.setGatewayConfig(GatewayConfig(url, token))
            note("حُفظت إعدادات البوابة")
        }
    }

    fun testGateway(url: String, token: String) {
        viewModelScope.launch {
            _state.update { it.copy(gatewayTesting = true, gatewayTestMessage = null) }
            val message = try {
                val response = gateway.testConnection(GatewayConfig(url.trim(), token.trim()))
                "✓ البوابة تعمل" + (response.model?.let { " · النموذج: $it" } ?: " · الكود قديم، حدّثه لتفعيل التبديل بين النماذج")
            } catch (e: GatewayException) {
                "✗ " + e.message
            }
            _state.update { it.copy(gatewayTesting = false, gatewayTestMessage = message) }
        }
    }

    // ------------------------------------------------------------------ picture and schedule

    fun pickPicture(uri: Uri) {
        viewModelScope.launch {
            val file = pictures.import(uri)
            if (file == null) {
                note("تعذّر قراءة الصورة، جرّب صورة أخرى")
                return@launch
            }
            val previous = _state.value.pictureFile
            _state.update { it.copy(pictureFile = file, dirty = true) }
            deleteIfOrphan(previous)
            refreshVisuals()
        }
    }

    fun removePicture() {
        val previous = _state.value.pictureFile
        _state.update { it.copy(pictureFile = null, dirty = true) }
        deleteIfOrphan(previous)
        refreshVisuals()
    }

    /** A picture nobody saved can be deleted when it is replaced; one a saved post uses must stay. */
    private fun deleteIfOrphan(file: String?) {
        if (file == null) return
        if (_state.value.saved.none { it.picturePath == file }) pictures.delete(file)
    }

    fun setScheduleDate(date: String) {
        val time = _state.value.scheduledAt?.substringAfter('T', DEFAULT_TIME) ?: DEFAULT_TIME
        _state.update { it.copy(scheduledAt = date + "T" + time, dirty = true) }
    }

    fun setScheduleTime(hour: Int, minute: Int) {
        val date = _state.value.scheduledAt?.substringBefore('T') ?: DateUtil.today()
        val time = String.format(Locale.US, "%02d:%02d", hour, minute)
        _state.update { it.copy(scheduledAt = date + "T" + time, dirty = true) }
    }

    fun clearSchedule() {
        _state.update { it.copy(scheduledAt = null, dirty = true) }
    }

    // ------------------------------------------------------------------ campaign

    /**
     * Links the post to a campaign (or, with null, unlinks it). The campaign's code rides on the links of the text,
     * so the old link is swapped for the new one in every formula: nothing the person typed is lost.
     */
    fun selectCampaign(id: String?) {
        val s = _state.value
        val next = id?.let { wanted -> s.campaigns.firstOrNull { it.id == wanted } }
        val oldLink = LinkBuilder.build(s.brand, s.channel, s.campaign?.code)
        val newLink = LinkBuilder.build(s.brand, s.channel, next?.code)
        _state.update { st ->
            st.copy(
                campaignId = next?.id,
                formulas = st.formulas.map { formula -> formula.copy(text = formula.text.replace(oldLink, newLink)) },
                dirty = true,
            )
        }
        recheck()
    }

    /** Makes a campaign from just its name (more is set on the campaigns screen) and links the post to it. */
    fun createCampaign(name: String) {
        val clean = name.trim()
        if (clean.isEmpty()) return note("اكتب اسم الحملة")
        viewModelScope.launch {
            val campaign = campaignRepository.create(clean, "", null, null)
            _state.update { st ->
                if (st.campaigns.any { it.id == campaign.id }) st else st.copy(campaigns = listOf(campaign) + st.campaigns)
            }
            selectCampaign(campaign.id)
            note("أُنشئت الحملة «${campaign.name}» برمز ${campaign.code}")
        }
    }

    // ------------------------------------------------------------------ save, restore, delete

    fun save(approve: Boolean) {
        val s = _state.value
        val product = s.product ?: return
        if (s.currentText.isBlank()) return note("اكتب نصًا أولًا")
        if (approve && !s.check.passed) return note("لا يُعتمد: " + s.check.errors.first().message)
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = s.editingId ?: UUID.randomUUID().toString()
            val existing = s.editingId?.let { contentRepository.get(it) }
            contentRepository.save(
                SavedContent(
                    id = id,
                    productId = product.id,
                    productName = product.name,
                    channel = s.channel,
                    text = s.currentText,
                    status = if (approve) ContentStatus.APPROVED else ContentStatus.DRAFT,
                    designId = s.designId,
                    picturePath = s.pictureFile,
                    scheduledAt = s.scheduledAt,
                    campaignId = s.campaign?.id,
                    campaignCode = s.campaign?.code,
                    tone = s.tone,
                    angle = s.angle,
                    createdAt = existing?.createdAt ?: now,
                    updatedAt = now,
                ),
            )
            _state.update { it.copy(editingId = id, dirty = false) }
            textEdited = false
            note(if (approve) "اعتُمد المحتوى" else "حُفظت المسودة")
        }
    }

    /** Opens a saved post for editing: text, channel, design and picture come back as they were saved. */
    fun loadSaved(content: SavedContent) {
        val s = _state.value
        val productKnown = s.products.any { it.id == content.productId }
        _state.update {
            it.copy(
                productId = if (productKnown) content.productId else it.productId,
                channel = content.channel,
                tone = content.tone?.takeIf { tone -> tone in it.tones } ?: it.tone,
                designId = content.designId,
                pictureFile = content.picturePath,
                scheduledAt = content.scheduledAt,
                campaignId = content.campaignId?.takeIf { id -> it.campaigns.any { campaign -> campaign.id == id } },
                formulas = listOf(Formula(LOADED_FORMULA, content.text)),
                formulaIndex = 0,
                editingId = content.id,
                dirty = false,
                openTool = null,
                ideas = emptyList(),
            )
        }
        textEdited = false
        recheck()
        refreshVisuals()
        if (!productKnown) note("الصنف المرتبط لم يعد في الكتالوج، فُتح بالصنف الحالي")
    }

    fun deleteSaved(id: String) {
        viewModelScope.launch {
            contentRepository.delete(id)
            if (_state.value.editingId == id) _state.update { it.copy(editingId = null) }
            note("حُذف المحتوى")
        }
    }

    // ------------------------------------------------------------------ share and export

    fun share(target: ShareTarget) {
        val s = _state.value
        val product = s.product ?: return
        if (s.currentText.isBlank()) return note("اكتب نصًا أولًا")
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.Default) {
                    renderer.renderFull(product, s.brand, s.channel, s.designId, s.effectivePicture)
                }
                val uri = exporter.shareUri(bitmap)
                _events.emit(StudioEvent.Share(uri, s.currentText, target.packageName))
            } catch (e: IOException) {
                note("تعذّر تجهيز الصورة للمشاركة")
            }
        }
    }

    fun saveImage() {
        val s = _state.value
        val product = s.product ?: return
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.Default) {
                    renderer.renderFull(product, s.brand, s.channel, s.designId, s.effectivePicture)
                }
                note("حُفظت الصورة في " + exporter.saveToDownloads(bitmap))
            } catch (e: IOException) {
                note(e.message ?: "تعذّر حفظ الصورة")
            }
        }
    }

    // ------------------------------------------------------------------ rendering

    private fun refreshVisuals() {
        requestPreview()
        if (_state.value.openTool == StudioTool.DESIGN) requestThumbs()
    }

    private fun requestPreview() {
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            val s = _state.value
            val product = s.product ?: return@launch
            val bitmap = withContext(Dispatchers.Default) {
                renderer.render(product, s.brand, s.channel, s.designId, s.effectivePicture, PREVIEW_WIDTH)
            }
            _state.update { it.copy(preview = bitmap) }
        }
    }

    private fun requestThumbs() {
        thumbsJob?.cancel()
        thumbsJob = viewModelScope.launch {
            val s = _state.value
            val product = s.product ?: return@launch
            val thumbs = withContext(Dispatchers.Default) {
                renderer.designs.associate { design ->
                    design.id to renderer.render(product, s.brand, s.channel, design.id, s.effectivePicture, THUMB_WIDTH)
                }
            }
            _state.update { it.copy(thumbs = thumbs) }
        }
    }

    /** A stored picture for a small card (the product grid). */
    suspend fun loadThumb(fileName: String): android.graphics.Bitmap? =
        withContext(Dispatchers.IO) { pictures.load(fileName, CARD_THUMB_SIDE) }

    // ------------------------------------------------------------------ small helpers

    private fun note(text: String) {
        _events.tryEmit(StudioEvent.Message(text))
    }

    private fun persistPrefs() {
        val s = _state.value
        viewModelScope.launch {
            settings.setStudioPrefs(StudioPrefs(s.channel.code, s.tone, s.angle, s.designId))
        }
    }

    private fun persistUsed() {
        val snapshot = used
        viewModelScope.launch { settings.setUsedPhrases(snapshot) }
    }

    private companion object {
        const val PREVIEW_WIDTH = 540
        const val THUMB_WIDTH = 220
        const val CARD_THUMB_SIDE = 240
        const val PREVIEW_DEBOUNCE_MS = 120L
        const val DEFAULT_TIME = "09:00"
        const val IDEA_FORMULA = "فكرة"
        const val EDITED_FORMULA = "معدّل"
        const val LOADED_FORMULA = "تعديل"
    }
}
