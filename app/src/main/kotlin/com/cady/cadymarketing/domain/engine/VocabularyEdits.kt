package com.cady.cadymarketing.domain.engine

/** The six lists a tone is made of. [required] ones must never be empty or the generator has nothing to write. */
enum class VocabCategory(val key: String, val label: String, val required: Boolean) {
    HOOKS("hooks", "افتتاحيات", true),
    PRICES("prices", "أسطر السعر", true),
    OFFERS("offers", "أسطر العرض", true),
    CALLS("calls", "دعوات الطلب", true),
    URGENCY("urgency", "استعجال", false),
    QUESTIONS("questions", "أسئلة", false),
    ;

    fun linesOf(vocabulary: ToneVocabulary): List<String> = when (this) {
        HOOKS -> vocabulary.hooks
        PRICES -> vocabulary.prices
        OFFERS -> vocabulary.offers
        CALLS -> vocabulary.calls
        URGENCY -> vocabulary.urgency
        QUESTIONS -> vocabulary.questions
    }

    companion object {
        fun fromKey(key: String?): VocabCategory? = entries.firstOrNull { it.key == key }
    }
}

/** What was changed on top of one built-in list: lines added, and built-in lines hidden. */
data class LineEdits(val added: List<String> = emptyList(), val hidden: Set<String> = emptySet()) {
    val isUntouched: Boolean get() = added.isEmpty() && hidden.isEmpty()
}

/** Keys of the edited lists. One key space for tones and ideas keeps the editing code in one place. */
object VocabKey {
    fun tone(tone: String, category: VocabCategory): String = "t|$tone|${category.key}"

    fun idea(angle: String): String = "i|$angle"

    fun tonePrefix(tone: String): String = "t|$tone|"
}

/**
 * Everything the manager changed in the vocabulary, on top of the defaults that are built into the code.
 * Built-in lines are never copied here: a built-in line that was removed is only remembered as hidden, so a
 * future version of the app can improve the defaults without losing anyone's edits.
 */
data class VocabularyEdits(
    val lines: Map<String, LineEdits> = emptyMap(),
    val customTones: List<String> = emptyList(),
    val hiddenTones: Set<String> = emptySet(),
    val customAngles: List<String> = emptyList(),
    val hiddenAngles: Set<String> = emptySet(),
    val templates: List<TextTemplate> = emptyList(),
    val hiddenTemplates: Set<String> = emptySet(),
    val updatedAt: Long = 0L,
) {
    fun lineEdits(key: String): LineEdits = lines[key] ?: LineEdits()

    private fun put(key: String, edits: LineEdits): VocabularyEdits =
        copy(lines = if (edits.isUntouched) lines - key else lines + (key to edits))

    /** Adds a line. Adding a hidden built-in line just shows it again. Blank and repeated lines are ignored. */
    fun addLine(key: String, line: String): VocabularyEdits {
        val clean = line.trim()
        if (clean.isEmpty()) return this
        val current = lineEdits(key)
        if (clean in current.hidden) return put(key, current.copy(hidden = current.hidden - clean))
        if (clean in current.added) return this
        return put(key, current.copy(added = current.added + clean))
    }

    /** Removes a line you added, or hides a built-in one ([builtIn] true). */
    fun removeLine(key: String, line: String, builtIn: Boolean): VocabularyEdits {
        val current = lineEdits(key)
        return if (builtIn) {
            put(key, current.copy(hidden = current.hidden + line))
        } else {
            put(key, current.copy(added = current.added - line))
        }
    }

    /** Replaces a line you added (built-in lines cannot be edited, only hidden and re-added). */
    fun replaceLine(key: String, old: String, new: String): VocabularyEdits {
        val clean = new.trim()
        if (clean.isEmpty()) return this
        val current = lineEdits(key)
        if (old !in current.added) return addLine(key, clean)
        val replaced = current.added.map { if (it == old) clean else it }.distinct()
        return put(key, current.copy(added = replaced))
    }

    fun restoreLine(key: String, line: String): VocabularyEdits {
        val current = lineEdits(key)
        return put(key, current.copy(hidden = current.hidden - line))
    }

    /** Back to the built-in list: forgets every addition and every hidden line of that list. */
    fun resetLines(key: String): VocabularyEdits = copy(lines = lines - key)

    // ------------------------------------------------------------------ tones

    /** A new tone that starts as a copy of [from] (so it is never empty). The name must be new. */
    fun addTone(name: String, from: ToneVocabulary): VocabularyEdits {
        val clean = name.trim()
        if (clean.isEmpty() || clean in customTones) return this
        var next = copy(customTones = customTones + clean, hiddenTones = hiddenTones - clean)
        for (category in VocabCategory.entries) {
            for (line in category.linesOf(from)) next = next.addLine(VocabKey.tone(clean, category), line)
        }
        return next
    }

    /** Removes a tone you created (with its lines). */
    fun removeCustomTone(name: String): VocabularyEdits = copy(
        customTones = customTones - name,
        lines = lines.filterKeys { !it.startsWith(VocabKey.tonePrefix(name)) },
    )

    fun hideTone(name: String): VocabularyEdits = copy(hiddenTones = hiddenTones + name)

    fun restoreTone(name: String): VocabularyEdits = copy(hiddenTones = hiddenTones - name)

    // ------------------------------------------------------------------ angles (idea lists)

    fun addAngle(name: String, from: List<String>): VocabularyEdits {
        val clean = name.trim()
        if (clean.isEmpty() || clean in customAngles) return this
        var next = copy(customAngles = customAngles + clean, hiddenAngles = hiddenAngles - clean)
        for (line in from) next = next.addLine(VocabKey.idea(clean), line)
        return next
    }

    fun removeCustomAngle(name: String): VocabularyEdits = copy(
        customAngles = customAngles - name,
        lines = lines - VocabKey.idea(name),
    )

    fun hideAngle(name: String): VocabularyEdits = copy(hiddenAngles = hiddenAngles + name)

    fun restoreAngle(name: String): VocabularyEdits = copy(hiddenAngles = hiddenAngles - name)

    // ------------------------------------------------------------------ templates

    fun addTemplate(template: TextTemplate): VocabularyEdits =
        copy(templates = templates.filter { it.id != template.id } + template)

    fun updateTemplate(template: TextTemplate): VocabularyEdits =
        copy(templates = templates.map { if (it.id == template.id) template else it })

    fun removeTemplate(id: String): VocabularyEdits = copy(templates = templates.filter { it.id != id })

    fun hideBuiltInTemplate(id: String): VocabularyEdits = copy(hiddenTemplates = hiddenTemplates + id)

    fun restoreBuiltInTemplate(id: String): VocabularyEdits = copy(hiddenTemplates = hiddenTemplates - id)

    val isUntouched: Boolean
        get() = lines.isEmpty() && customTones.isEmpty() && hiddenTones.isEmpty() && customAngles.isEmpty() &&
            hiddenAngles.isEmpty() && templates.isEmpty() && hiddenTemplates.isEmpty()
}

/** The vocabulary the engines really use: the built-in defaults with the edits applied. */
data class MergedVocabulary(
    val tones: Map<String, ToneVocabulary>,
    val ideas: Map<String, List<String>>,
    val templates: List<TextTemplate>,
    val priceAngles: Set<String>,
)

object VocabularyMerge {
    private val emptyTone = ToneVocabulary(emptyList(), emptyList(), emptyList(), emptyList())

    fun merge(
        edits: VocabularyEdits,
        defaultTones: Map<String, ToneVocabulary> = DefaultVocabulary.tones,
        defaultIdeas: Map<String, List<String>> = DefaultIdeas.byAngle,
        defaultTemplates: List<TextTemplate> = DefaultVocabulary.templates,
        defaultPriceAngles: Set<String> = DefaultIdeas.withPriceLine,
    ): MergedVocabulary {
        val tones = LinkedHashMap<String, ToneVocabulary>()
        for ((name, base) in defaultTones) {
            if (name !in edits.hiddenTones) tones[name] = mergeTone(name, base, edits)
        }
        for (name in edits.customTones) {
            if (name !in tones) tones[name] = mergeTone(name, emptyTone, edits)
        }
        // Hiding every tone would leave nothing to write with, so the defaults come back.
        if (tones.isEmpty()) tones.putAll(defaultTones)

        val ideas = LinkedHashMap<String, List<String>>()
        for ((angle, base) in defaultIdeas) {
            if (angle !in edits.hiddenAngles) ideas[angle] = lines(VocabKey.idea(angle), base, edits).ifEmpty { base }
        }
        for (angle in edits.customAngles) {
            if (angle !in ideas) {
                val own = lines(VocabKey.idea(angle), emptyList(), edits)
                if (own.isNotEmpty()) ideas[angle] = own
            }
        }
        if (ideas.isEmpty()) ideas.putAll(defaultIdeas)

        val templates = defaultTemplates.filter { it.id !in edits.hiddenTemplates } + edits.templates
        return MergedVocabulary(tones, ideas, templates, defaultPriceAngles.filter { it in ideas }.toSet())
    }

    private fun mergeTone(name: String, base: ToneVocabulary, edits: VocabularyEdits): ToneVocabulary {
        fun pick(category: VocabCategory): List<String> {
            val builtIn = category.linesOf(base)
            val merged = lines(VocabKey.tone(name, category), builtIn, edits)
            return if (merged.isEmpty() && category.required) builtIn else merged
        }
        return ToneVocabulary(
            hooks = pick(VocabCategory.HOOKS),
            prices = pick(VocabCategory.PRICES),
            offers = pick(VocabCategory.OFFERS),
            calls = pick(VocabCategory.CALLS),
            urgency = pick(VocabCategory.URGENCY),
            questions = pick(VocabCategory.QUESTIONS),
        )
    }

    private fun lines(key: String, builtIn: List<String>, edits: VocabularyEdits): List<String> {
        val change = edits.lines[key] ?: return builtIn
        return (builtIn.filter { it !in change.hidden } + change.added).distinct()
    }
}

/**
 * The same rules the built-in vocabulary obeys (VocabularyTest): no digits (a number in a line would be
 * rejected by the claim checker later), no banned phrase, only known {placeholders}, and the few shape
 * rules the generator relies on.
 */
object VocabularyLint {
    val knownPlaceholders: List<String> =
        listOf("الاسم", "الوحدة", "السعر", "السعر_قبل", "الفرق", "الانتهاء", "الرابط", "العلامة")

    private val token = Regex("\\{(.+?)\\}")
    private const val DIGITS = "٠١٢٣٤٥٦٧٨٩۰۱۲۳۴۵۶۷۸۹"

    /** Problems of one line (a template when [category] is null). [allowCustomerName] lets {اسم العميل} through. */
    fun problems(
        line: String,
        category: VocabCategory?,
        bannedPhrases: List<String>,
        allowCustomerName: Boolean = false,
    ): List<String> {
        val list = ArrayList<String>()
        if (line.isBlank()) {
            list.add("السطر فارغ")
            return list
        }
        if (line.any { it.isDigit() || it in DIGITS }) {
            list.add("لا تكتب أرقامًا في السطر: استعمل متغيرًا مثل {السعر}")
        }
        for (word in bannedPhrases) {
            if (line.contains(word)) list.add("فيه عبارة ممنوعة: $word")
        }
        for (found in token.findAll(line)) {
            val name = found.groupValues[1]
            val customer = allowCustomerName && found.value == Placeholders.CUSTOMER_NAME
            if (name !in knownPlaceholders && !customer) list.add("متغير غير معروف: ${found.value}")
        }
        when (category) {
            VocabCategory.PRICES, VocabCategory.OFFERS ->
                if (!line.contains("{السعر}")) list.add("سطر السعر يجب أن يحوي {السعر}")
            VocabCategory.CALLS ->
                if (!line.trim().endsWith("{الرابط}")) list.add("دعوة الطلب يجب أن تنتهي بـ {الرابط}")
            VocabCategory.URGENCY ->
                if (!line.contains("{الانتهاء}")) list.add("سطر الاستعجال يجب أن يحوي {الانتهاء}")
            else -> Unit
        }
        return list.distinct()
    }
}

/** The edits as a stored document (also what syncs). Anything missing or broken reads as "no edits". */
object VocabularyJson {
    fun encode(edits: VocabularyEdits): String {
        val lines = LinkedHashMap<String, Any?>()
        for ((key, change) in edits.lines) {
            lines[key] = linkedMapOf("add" to change.added, "hide" to change.hidden.toList())
        }
        val templates = edits.templates.map { linkedMapOf("id" to it.id, "name" to it.name, "text" to it.text) }
        return MiniJson.write(
            linkedMapOf(
                "v" to 1,
                "lines" to lines,
                "customTones" to edits.customTones,
                "hiddenTones" to edits.hiddenTones.toList(),
                "customAngles" to edits.customAngles,
                "hiddenAngles" to edits.hiddenAngles.toList(),
                "templates" to templates,
                "hiddenTemplates" to edits.hiddenTemplates.toList(),
                "updatedAt" to edits.updatedAt,
            ),
        )
    }

    fun decode(json: String?): VocabularyEdits {
        val root = J.parseMap(json)
        if (root.isEmpty()) return VocabularyEdits()
        val lines = LinkedHashMap<String, LineEdits>()
        for ((key, value) in J.map(root["lines"])) {
            val change = J.map(value)
            val edits = LineEdits(J.strings(change["add"]), J.strings(change["hide"]).toSet())
            if (!edits.isUntouched) lines[key] = edits
        }
        val templates = J.list(root["templates"]).mapNotNull { item ->
            val t = J.map(item)
            val id = J.str(t, "id")
            val name = J.str(t, "name")
            val text = J.str(t, "text")
            if (id.isNullOrBlank() || name.isNullOrBlank() || text == null) null else TextTemplate(id, name, text)
        }
        return VocabularyEdits(
            lines = lines,
            customTones = J.strings(root["customTones"]),
            hiddenTones = J.strings(root["hiddenTones"]).toSet(),
            customAngles = J.strings(root["customAngles"]),
            hiddenAngles = J.strings(root["hiddenAngles"]).toSet(),
            templates = templates,
            hiddenTemplates = J.strings(root["hiddenTemplates"]).toSet(),
            updatedAt = J.long(root, "updatedAt") ?: 0L,
        )
    }
}
