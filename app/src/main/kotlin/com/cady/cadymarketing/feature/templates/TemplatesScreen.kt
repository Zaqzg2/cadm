package com.cady.cadymarketing.feature.templates

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.ConfirmDialog
import com.cady.cadymarketing.core.components.Pill
import com.cady.cadymarketing.data.product.SampleCatalog
import com.cady.cadymarketing.domain.engine.DefaultVocabulary
import com.cady.cadymarketing.domain.engine.LineEdits
import com.cady.cadymarketing.domain.engine.Placeholders
import com.cady.cadymarketing.domain.engine.TextTemplate
import com.cady.cadymarketing.domain.engine.VocabCategory
import com.cady.cadymarketing.domain.engine.VocabularyLint
import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Channel

/** What the open dialog is for. */
private sealed interface Editing {
    data class Line(val key: String, val category: VocabCategory?, val old: String?) : Editing

    data class Template(val template: TextTemplate?) : Editing

    data object NewTone : Editing

    data object NewAngle : Editing

    data class ConfirmReset(val key: String) : Editing

    data class ConfirmDeleteTone(val name: String, val custom: Boolean) : Editing

    data class ConfirmDeleteAngle(val name: String, val custom: Boolean) : Editing
}

/** One line of a list, with where it comes from and whether it is currently hidden. */
private data class LineView(val text: String, val builtIn: Boolean, val hidden: Boolean)

private fun lineViews(builtIn: List<String>, edits: LineEdits): List<LineView> =
    builtIn.map { LineView(it, true, it in edits.hidden) } + edits.added.filter { it !in builtIn }.map { LineView(it, false, false) }

/**
 * The vocabulary the text generator writes from. The built-in bank stays in the app; what is changed here sits
 * on top of it (added lines, hidden lines, new tones, new templates) and can always be undone.
 */
@Composable
fun TemplatesScreen(onBack: () -> Unit, viewModel: TemplatesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var editing by remember { mutableStateOf<Editing?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { text ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    CadyScreen(
        title = "القوالب والمفردات",
        onBack = onBack,
        withBottomInset = true,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loaded) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@CadyScreen
        }
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TemplatesSection.entries.forEach { section ->
                    FilterChip(
                        selected = state.section == section,
                        onClick = { viewModel.selectSection(section) },
                        label = { Text(section.label) },
                    )
                }
            }
            when (state.section) {
                TemplatesSection.WORDS -> WordsSection(state, viewModel) { editing = it }
                TemplatesSection.IDEAS -> IdeasSection(state, viewModel) { editing = it }
                TemplatesSection.TEMPLATES -> TemplatesList(state, viewModel) { editing = it }
            }
        }
    }

    when (val dialog = editing) {
        null -> Unit
        is Editing.Line -> LineDialog(
            title = if (dialog.old == null) "سطر جديد" else "تعديل السطر",
            initial = dialog.old.orEmpty(),
            multiline = false,
            problems = { text -> viewModel.problemsOf(text, dialog.category) },
            brand = state.brand,
            onSave = { text ->
                viewModel.saveLine(dialog.key, dialog.category, dialog.old, text)
                editing = null
            },
            onDismiss = { editing = null },
        )
        is Editing.Template -> TemplateDialog(
            existing = dialog.template,
            problems = { text -> viewModel.problemsOf(text, null, template = true) },
            brand = state.brand,
            onSave = { name, text ->
                viewModel.saveTemplate(dialog.template, name, text)
                editing = null
            },
            onDismiss = { editing = null },
        )
        Editing.NewTone -> NewNameDialog(
            title = "نبرة جديدة",
            label = "اسم النبرة",
            copyLabel = "تبدأ بنسخة من",
            sources = state.merged.tones.keys.toList(),
            selected = state.currentTone,
            onCreate = { name, source ->
                viewModel.addTone(name, source)
                editing = null
            },
            onDismiss = { editing = null },
        )
        Editing.NewAngle -> NewNameDialog(
            title = "زاوية جديدة",
            label = "اسم الزاوية",
            copyLabel = "تبدأ بنسخة من",
            sources = state.merged.ideas.keys.toList(),
            selected = state.currentAngle,
            onCreate = { name, source ->
                viewModel.addAngle(name, source)
                editing = null
            },
            onDismiss = { editing = null },
        )
        is Editing.ConfirmReset -> ConfirmDialog(
            title = "استعادة القائمة الافتراضية؟",
            message = "تُحذف أسطرك المضافة في هذه القائمة، وتعود الأسطر المخفية.",
            confirmLabel = "استعادة",
            onConfirm = {
                viewModel.resetLines(dialog.key)
                editing = null
            },
            onDismiss = { editing = null },
        )
        is Editing.ConfirmDeleteTone -> ConfirmDialog(
            title = if (dialog.custom) "حذف النبرة؟" else "إخفاء النبرة؟",
            message = if (dialog.custom) "تُحذف النبرة «${dialog.name}» بكل أسطرها." else "تختفي النبرة «${dialog.name}» من الاستوديو، ويمكنك إظهارها لاحقًا.",
            confirmLabel = if (dialog.custom) "حذف" else "إخفاء",
            onConfirm = {
                if (dialog.custom) viewModel.removeCustomTone(dialog.name) else viewModel.hideTone(dialog.name)
                editing = null
            },
            onDismiss = { editing = null },
        )
        is Editing.ConfirmDeleteAngle -> ConfirmDialog(
            title = if (dialog.custom) "حذف الزاوية؟" else "إخفاء الزاوية؟",
            message = if (dialog.custom) "تُحذف الزاوية «${dialog.name}» بكل أفكارها." else "تختفي الزاوية «${dialog.name}» من الاستوديو، ويمكنك إظهارها لاحقًا.",
            confirmLabel = if (dialog.custom) "حذف" else "إخفاء",
            onConfirm = {
                if (dialog.custom) viewModel.removeCustomAngle(dialog.name) else viewModel.hideAngle(dialog.name)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }
}

// ---------------------------------------------------------------------- words

@Composable
private fun WordsSection(state: TemplatesState, vm: TemplatesViewModel, open: (Editing) -> Unit) {
    val tone = state.currentTone
    val custom = tone in state.edits.customTones
    val key = state.wordsKey
    val change = state.edits.lineEdits(key)
    val lines = lineViews(state.builtInWords(), change)
    val shown = lines.count { !it.hidden }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            ChipRow(state.merged.tones.keys.toList(), tone) { vm.selectTone(it) }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { open(Editing.NewTone) }) { Text("+ نبرة جديدة") }
                TextButton(onClick = { open(Editing.ConfirmDeleteTone(tone, custom)) }) {
                    Text(if (custom) "حذف هذه النبرة" else "إخفاء هذه النبرة")
                }
            }
            val hiddenTones = state.edits.hiddenTones.filter { it in DefaultVocabulary.tones.keys }
            if (hiddenTones.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("نبرات مخفية:", style = MaterialTheme.typography.labelMedium)
                    hiddenTones.forEach { name ->
                        AssistChip(onClick = { vm.restoreTone(name) }, label = { Text("إظهار $name") })
                    }
                }
            }
        }
        item {
            ChipRow(VocabCategory.entries.toList().map { it.label }, state.category.label) { label ->
                VocabCategory.entries.firstOrNull { it.label == label }?.let { vm.selectCategory(it) }
            }
        }
        item {
            ListHeader(
                count = shown,
                canReset = !change.isUntouched,
                onAdd = { open(Editing.Line(key, state.category, null)) },
                onReset = { open(Editing.ConfirmReset(key)) },
            )
        }
        lineItems(lines) { line ->
            LineCard(
                line = line,
                onEdit = { open(Editing.Line(key, state.category, line.text)) },
                onRemove = { vm.removeLine(key, line.text, line.builtIn, shown, state.category.required) },
                onRestore = { vm.restoreLine(key, line.text) },
            )
        }
        item { Hint(categoryHint(state.category)) }
    }
}

// ---------------------------------------------------------------------- ideas

@Composable
private fun IdeasSection(state: TemplatesState, vm: TemplatesViewModel, open: (Editing) -> Unit) {
    val angle = state.currentAngle
    val custom = angle in state.edits.customAngles
    val key = state.ideasKey
    val change = state.edits.lineEdits(key)
    val lines = lineViews(state.builtInIdeas(), change)
    val shown = lines.count { !it.hidden }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { ChipRow(state.merged.ideas.keys.toList(), angle) { vm.selectAngle(it) } }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { open(Editing.NewAngle) }) { Text("+ زاوية جديدة") }
                TextButton(onClick = { open(Editing.ConfirmDeleteAngle(angle, custom)) }) {
                    Text(if (custom) "حذف هذه الزاوية" else "إخفاء هذه الزاوية")
                }
            }
            val hiddenAngles = state.edits.hiddenAngles.toList()
            if (hiddenAngles.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("زوايا مخفية:", style = MaterialTheme.typography.labelMedium)
                    hiddenAngles.forEach { name ->
                        AssistChip(onClick = { vm.restoreAngle(name) }, label = { Text("إظهار $name") })
                    }
                }
            }
        }
        item {
            ListHeader(
                count = shown,
                canReset = !change.isUntouched,
                onAdd = { open(Editing.Line(key, null, null)) },
                onReset = { open(Editing.ConfirmReset(key)) },
            )
        }
        lineItems(lines) { line ->
            LineCard(
                line = line,
                onEdit = { open(Editing.Line(key, null, line.text)) },
                onRemove = { vm.removeLine(key, line.text, line.builtIn, shown, true) },
                onRestore = { vm.restoreLine(key, line.text) },
            )
        }
        item { Hint("الفكرة جملة افتتاحية قصيرة. «اكتب منشورًا» في الاستوديو يحوّلها إلى منشور كامل بسطر سعر ودعوة للطلب.") }
    }
}

// ---------------------------------------------------------------------- templates

@Composable
private fun TemplatesList(state: TemplatesState, vm: TemplatesViewModel, open: (Editing) -> Unit) {
    val hiddenBuiltIn = DefaultVocabulary.templates.filter { it.id in state.edits.hiddenTemplates }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Button(onClick = { open(Editing.Template(null)) }) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("قالب جديد")
            }
        }
        lineItems(state.merged.templates) { template ->
            val builtIn = DefaultVocabulary.templates.any { it.id == template.id }
            CadyCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(template.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Pill(if (builtIn) "افتراضي" else "مضاف")
                        if (!builtIn) {
                            IconButton(onClick = { open(Editing.Template(template)) }) {
                                Icon(Icons.Filled.Edit, contentDescription = "تعديل")
                            }
                            IconButton(onClick = { vm.removeTemplate(template.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "حذف")
                            }
                        } else {
                            IconButton(onClick = { vm.hideBuiltInTemplate(template.id) }) {
                                Icon(Icons.Filled.VisibilityOff, contentDescription = "إخفاء")
                            }
                        }
                    }
                    Text(template.text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (hiddenBuiltIn.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("قوالب مخفية:", style = MaterialTheme.typography.labelMedium)
                    hiddenBuiltIn.forEach { t ->
                        AssistChip(onClick = { vm.restoreBuiltInTemplate(t.id) }, label = { Text("إظهار ${t.name}") })
                    }
                }
            }
        }
        item { Hint("القالب نص كامل يُملأ بالصنف فيظهر صيغةً جاهزة في الاستوديو. المتغيرات تُكتب بين أقواس مثل {الاسم} و{السعر}.") }
    }
}

// ---------------------------------------------------------------------- pieces

private fun <T> androidx.compose.foundation.lazy.LazyListScope.lineItems(items: List<T>, content: @Composable (T) -> Unit) {
    items.forEach { entry -> item { content(entry) } }
}

@Composable
private fun ChipRow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { name ->
            FilterChip(selected = name == selected, onClick = { onSelect(name) }, label = { Text(name) })
        }
    }
}

@Composable
private fun ListHeader(count: Int, canReset: Boolean, onAdd: () -> Unit, onReset: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("$count سطرًا", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
        if (canReset) TextButton(onClick = onReset) { Text("استعادة الافتراضي") }
        Button(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(Modifier.width(4.dp))
            Text("سطر جديد")
        }
    }
}

@Composable
private fun LineCard(line: LineView, onEdit: () -> Unit, onRemove: () -> Unit, onRestore: () -> Unit) {
    CadyCard(modifier = Modifier.fillMaxWidth().alpha(if (line.hidden) 0.55f else 1f)) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(line.text, style = MaterialTheme.typography.bodyMedium)
                Pill(
                    text = when {
                        line.hidden -> "مخفي"
                        line.builtIn -> "افتراضي"
                        else -> "مضاف"
                    },
                )
            }
            when {
                line.hidden -> IconButton(onClick = onRestore) { Icon(Icons.Filled.Restore, contentDescription = "إظهار") }
                line.builtIn -> IconButton(onClick = onRemove) { Icon(Icons.Filled.VisibilityOff, contentDescription = "إخفاء") }
                else -> {
                    IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "تعديل") }
                    IconButton(onClick = onRemove) { Icon(Icons.Filled.Delete, contentDescription = "حذف") }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

private fun categoryHint(category: VocabCategory): String = when (category) {
    VocabCategory.HOOKS -> "الافتتاحية أول سطر في المنشور. لا أرقام فيها، واستعمل {الاسم} و{العلامة}."
    VocabCategory.PRICES -> "سطر السعر يجب أن يحوي {السعر}، ويمكنه استعمال {الوحدة}."
    VocabCategory.OFFERS -> "سطر العرض يجب أن يحوي {السعر}، ويمكنه استعمال {السعر_قبل} و{الفرق}."
    VocabCategory.CALLS -> "دعوة الطلب يجب أن تنتهي بـ {الرابط}."
    VocabCategory.URGENCY -> "سطر الاستعجال يظهر حين يكون للعرض تاريخ انتهاء، ويجب أن يحوي {الانتهاء}."
    VocabCategory.QUESTIONS -> "سؤال يفتتح المنشور بدل الافتتاحية في الصيغة «سؤال»."
}

// ---------------------------------------------------------------------- dialogs

@Composable
private fun PlaceholderBar(onInsert: (String) -> Unit, includeCustomer: Boolean) {
    val names = VocabularyLint.knownPlaceholders.map { "{$it}" } + if (includeCustomer) listOf(Placeholders.CUSTOMER_NAME) else emptyList()
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        names.forEach { token -> AssistChip(onClick = { onInsert(token) }, label = { Text(token) }) }
    }
}

/** The fields shared by the line and template dialogs: the text, the placeholder buttons, the live check and a filled example. */
@Composable
private fun TextWithCheck(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    multiline: Boolean,
    problems: List<String>,
    brand: Brand,
    includeCustomer: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(label) },
            minLines = if (multiline) 4 else 2,
            maxLines = if (multiline) 10 else 4,
            isError = value.isNotBlank() && problems.isNotEmpty(),
        )
        PlaceholderBar(onInsert = { token -> onChange(if (value.isEmpty() || value.endsWith(" ") || value.endsWith("\n")) value + token else "$value $token") }, includeCustomer = includeCustomer)
        if (value.isNotBlank()) {
            problems.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (problems.isEmpty()) {
                val example = Placeholders.fill(value, SampleCatalog.products.first(), brand, Channel.WHATSAPP, null)
                Text("مثال:", style = MaterialTheme.typography.labelMedium)
                Text(example, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun LineDialog(
    title: String,
    initial: String,
    multiline: Boolean,
    problems: (String) -> List<String>,
    brand: Brand,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    val found = problems(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                TextWithCheck(text, { text = it }, "نص السطر", multiline, found, brand, includeCustomer = false)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text) }, enabled = found.isEmpty() && text.isNotBlank()) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun TemplateDialog(
    existing: TextTemplate?,
    problems: (String) -> List<String>,
    brand: Brand,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(existing?.name.orEmpty()) }
    var text by remember { mutableStateOf(existing?.text.orEmpty()) }
    val found = problems(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "قالب جديد" else "تعديل القالب") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("اسم القالب") },
                    singleLine = true,
                )
                TextWithCheck(text, { text = it }, "نص القالب", true, found, brand, includeCustomer = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, text) }, enabled = found.isEmpty() && text.isNotBlank() && name.isNotBlank()) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

@Composable
private fun NewNameDialog(
    title: String,
    label: String,
    copyLabel: String,
    sources: List<String>,
    selected: String,
    onCreate: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(selected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(label) },
                    singleLine = true,
                )
                Text(copyLabel, style = MaterialTheme.typography.labelLarge)
                ChipRow(sources, source) { source = it }
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name, source) }, enabled = name.isNotBlank()) { Text("إنشاء") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
