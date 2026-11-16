package com.cady.cadymarketing.feature.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cady.cadymarketing.data.brand.BrandRepository
import com.cady.cadymarketing.data.vocab.VocabularyRepository
import com.cady.cadymarketing.domain.engine.DefaultIdeas
import com.cady.cadymarketing.domain.engine.DefaultVocabulary
import com.cady.cadymarketing.domain.engine.MergedVocabulary
import com.cady.cadymarketing.domain.engine.TextTemplate
import com.cady.cadymarketing.domain.engine.VocabCategory
import com.cady.cadymarketing.domain.engine.VocabKey
import com.cady.cadymarketing.domain.engine.VocabularyEdits
import com.cady.cadymarketing.domain.engine.VocabularyLint
import com.cady.cadymarketing.domain.engine.VocabularyMerge
import com.cady.cadymarketing.domain.model.Brand
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class TemplatesSection(val label: String) {
    WORDS("المفردات"),
    TEMPLATES("القوالب"),
    IDEAS("الأفكار"),
}

data class TemplatesState(
    val loaded: Boolean = false,
    val edits: VocabularyEdits = VocabularyEdits(),
    val merged: MergedVocabulary = VocabularyMerge.merge(VocabularyEdits()),
    val brand: Brand = Brand(),
    val section: TemplatesSection = TemplatesSection.WORDS,
    val tone: String = DefaultVocabulary.DEFAULT_TONE,
    val category: VocabCategory = VocabCategory.HOOKS,
    val angle: String = "",
) {
    /** The selected tone, or the first one when the selected one was just removed or hidden. */
    val currentTone: String get() = if (tone in merged.tones) tone else merged.tones.keys.first()

    val currentAngle: String get() = if (angle in merged.ideas) angle else merged.ideas.keys.first()

    val wordsKey: String get() = VocabKey.tone(currentTone, category)

    val ideasKey: String get() = VocabKey.idea(currentAngle)

    /** The built-in lines of the selected list (none for a tone or angle the manager created). */
    fun builtInWords(): List<String> = DefaultVocabulary.tones[currentTone]?.let { category.linesOf(it) }.orEmpty()

    fun builtInIdeas(): List<String> = DefaultIdeas.byAngle[currentAngle].orEmpty()
}

@HiltViewModel
class TemplatesViewModel @Inject constructor(
    private val repository: VocabularyRepository,
    private val brandRepository: BrandRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(TemplatesState())
    val state: StateFlow<TemplatesState> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    init {
        viewModelScope.launch {
            repository.observeEdits().collect { edits ->
                _state.update { it.copy(loaded = true, edits = edits, merged = VocabularyMerge.merge(edits)) }
            }
        }
        viewModelScope.launch {
            brandRepository.observeBrand().collect { brand -> _state.update { it.copy(brand = brand) } }
        }
    }

    // ------------------------------------------------------------------ what is shown

    fun selectSection(section: TemplatesSection) = _state.update { it.copy(section = section) }

    fun selectTone(tone: String) = _state.update { it.copy(tone = tone) }

    fun selectCategory(category: VocabCategory) = _state.update { it.copy(category = category) }

    fun selectAngle(angle: String) = _state.update { it.copy(angle = angle) }

    // ------------------------------------------------------------------ lines (words and ideas)

    /** Problems of a line as typed, for the dialog's live check. Empty means it can be saved. */
    fun problemsOf(line: String, category: VocabCategory?, template: Boolean = false): List<String> =
        VocabularyLint.problems(line, category, _state.value.brand.bannedList, allowCustomerName = template)

    fun saveLine(key: String, category: VocabCategory?, old: String?, line: String) {
        val problems = problemsOf(line, category)
        if (problems.isNotEmpty()) return say(problems.first())
        change { edits -> if (old == null) edits.addLine(key, line) else edits.replaceLine(key, old, line) }
    }

    /** Removes a line you added, or hides a built-in one. The last line of a required list stays. */
    fun removeLine(key: String, line: String, builtIn: Boolean, shownCount: Int, required: Boolean) {
        if (required && shownCount <= 1) return say("لا يمكن حذف آخر سطر في هذه القائمة، أضف غيره أولًا")
        change { it.removeLine(key, line, builtIn) }
    }

    fun restoreLine(key: String, line: String) = change { it.restoreLine(key, line) }

    fun resetLines(key: String) = change { it.resetLines(key) }

    // ------------------------------------------------------------------ tones and angles

    fun addTone(name: String, copyFrom: String) {
        val clean = name.trim()
        val state = _state.value
        if (clean.isEmpty()) return say("اكتب اسم النبرة")
        if (clean in DefaultVocabulary.tones.keys || clean in state.edits.customTones) return say("هذا الاسم مستخدم")
        val source = state.merged.tones[copyFrom] ?: state.merged.tones.values.first()
        change { it.addTone(clean, source) }
        selectTone(clean)
    }

    fun removeCustomTone(name: String) = change { it.removeCustomTone(name) }

    fun hideTone(name: String) {
        if (_state.value.merged.tones.size <= 1) return say("لا يمكن إخفاء آخر نبرة")
        change { it.hideTone(name) }
    }

    fun restoreTone(name: String) = change { it.restoreTone(name) }

    fun addAngle(name: String, copyFrom: String) {
        val clean = name.trim()
        val state = _state.value
        if (clean.isEmpty()) return say("اكتب اسم الزاوية")
        if (clean in DefaultIdeas.byAngle.keys || clean in state.edits.customAngles) return say("هذا الاسم مستخدم")
        val source = state.merged.ideas[copyFrom] ?: state.merged.ideas.values.first()
        change { it.addAngle(clean, source) }
        selectAngle(clean)
    }

    fun removeCustomAngle(name: String) = change { it.removeCustomAngle(name) }

    fun hideAngle(name: String) {
        if (_state.value.merged.ideas.size <= 1) return say("لا يمكن إخفاء آخر زاوية")
        change { it.hideAngle(name) }
    }

    fun restoreAngle(name: String) = change { it.restoreAngle(name) }

    // ------------------------------------------------------------------ templates

    fun saveTemplate(existing: TextTemplate?, name: String, text: String) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return say("اكتب اسم القالب")
        val problems = problemsOf(text, null, template = true)
        if (problems.isNotEmpty()) return say(problems.first())
        if (existing == null) {
            val template = TextTemplate("u" + System.currentTimeMillis(), cleanName, text.trim())
            change { it.addTemplate(template) }
        } else {
            change { it.updateTemplate(existing.copy(name = cleanName, text = text.trim())) }
        }
    }

    fun removeTemplate(id: String) = change { it.removeTemplate(id) }

    fun hideBuiltInTemplate(id: String) = change { it.hideBuiltInTemplate(id) }

    fun restoreBuiltInTemplate(id: String) = change { it.restoreBuiltInTemplate(id) }

    // ------------------------------------------------------------------ helpers

    private fun change(transform: (VocabularyEdits) -> VocabularyEdits) {
        val current = _state.value.edits
        viewModelScope.launch { repository.save(transform(current)) }
    }

    private fun say(text: String) {
        _messages.tryEmit(text)
    }
}
