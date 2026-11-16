package com.cady.cadymarketing.feature.identity

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.ConfirmDialog
import com.cady.cadymarketing.core.components.LocalPicture
import com.cady.cadymarketing.core.components.SectionTitle
import com.cady.cadymarketing.core.poster.PosterDesigns
import com.cady.cadymarketing.domain.engine.BrandRules

private val mainColorPresets = listOf(
    "#c8102e", "#d84315", "#ef6c00", "#2e7d32", "#00796b", "#0277bd", "#1a237e", "#6a1b9a", "#ad1457", "#37474f",
)
private val accentColorPresets = listOf(
    "#ffc233", "#ffd54f", "#ffffff", "#ffab40", "#80cbc4", "#90caf9", "#ce93d8", "#f48fb1",
)

/**
 * The brand identity: name, colours, logo, voice, banned phrases, order number and link. The poster above the
 * form is drawn from the draft, so every change is seen before it is saved. The app's own colour follows the
 * main colour once saved.
 */
@Composable
fun IdentityScreen(onBack: () -> Unit, viewModel: IdentityViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var confirmLeave by remember { mutableStateOf(false) }
    var confirmDefaults by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { text ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }
    val logoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.pickLogo(uri)
    }
    val requestBack: () -> Unit = { if (state.dirty) confirmLeave = true else onBack() }
    BackHandler(enabled = state.dirty) { confirmLeave = true }

    CadyScreen(
        title = "الهوية",
        onBack = requestBack,
        withBottomInset = true,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            TextButton(onClick = viewModel::save, enabled = state.canSave) { Text("حفظ") }
        },
    ) { padding ->
        if (!state.loaded) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@CadyScreen
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            PosterPreview(state = state, onDesign = viewModel::onDesign)

            SectionTitle("الشعار")
            Row(verticalAlignment = Alignment.CenterVertically) {
                LocalPicture(
                    fileName = state.logoFile,
                    loader = viewModel::loadThumb,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit,
                )
                Spacer(Modifier.width(12.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(
                        onClick = { logoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    ) { Text(if (state.logoFile == null) "اختيار شعار" else "تغيير الشعار") }
                    if (state.logoFile != null) {
                        OutlinedButton(onClick = viewModel::removeLogo) { Text("إزالة") }
                    }
                }
            }
            Hint("بلا شعار يُكتب اسم العلامة على الملصق. يُفضَّل شعار PNG بخلفية شفافة. الشعار يُحفظ على هذا الجهاز فقط.")

            SectionTitle("الاسم")
            TextField("اسم العلامة", state.name, viewModel::onName)

            SectionTitle("الألوان")
            ColorField("اللون الأساسي (يلوّن التطبيق والملصقات)", state.primaryHex, mainColorPresets, viewModel::onPrimary)
            Spacer(Modifier.height(8.dp))
            ColorField("اللون الداكن (زاوية الملصق)", state.darkHex, emptyList(), viewModel::onDark)
            TextButton(onClick = viewModel::darkFromPrimary) { Text("ولّد الداكن من الأساسي") }
            ColorField("اللون المميز (الشارات والأزرار)", state.accentHex, accentColorPresets, viewModel::onAccent)

            SectionTitle("الصوت والعبارات")
            TextField("نبرة العلامة (تُرسل للذكاء)", state.tone, viewModel::onTone)
            Spacer(Modifier.height(8.dp))
            TextField(
                label = "العبارات الممنوعة (افصل بفاصلة)",
                value = state.banned,
                onChange = viewModel::onBanned,
                singleLine = false,
                supporting = "أي نص فيه إحداها لا يُعتمد، وتُهمل تلقائيًا من المفردات والمزايا.",
            )

            SectionTitle("الطلب والمتابعة")
            TextField("رقم واتساب للطلب", state.whatsapp, viewModel::onWhatsapp, ltr = true, keyboard = KeyboardType.Phone)
            Spacer(Modifier.height(8.dp))
            TextField("رابط الموقع", state.siteUrl, viewModel::onSite, ltr = true, keyboard = KeyboardType.Uri)
            Spacer(Modifier.height(8.dp))
            TextField(
                label = "الوسوم (إنستغرام وتيك توك)",
                value = state.hashtags,
                onChange = viewModel::onHashtags,
                supporting = "افصل بمسافة أو فاصلة. تُضاف # تلقائيًا، ويُستعمل أول ثلاثة.",
            )

            val problems = state.problems
            if (problems.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                problems.forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = { confirmDefaults = true }, modifier = Modifier.fillMaxWidth()) {
                Text("استعادة قيم الأداة الأصلية")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "تجاهل التعديلات؟",
            message = "لم تحفظ تعديلاتك على الهوية، وستضيع إن خرجت الآن.",
            confirmLabel = "تجاهل وخروج",
            onConfirm = {
                confirmLeave = false
                onBack()
            },
            onDismiss = { confirmLeave = false },
        )
    }
    if (confirmDefaults) {
        ConfirmDialog(
            title = "استعادة القيم الأصلية؟",
            message = "تعود كل الحقول (والشعار) إلى قيم الأداة الأصلية. لا يُحفظ شيء حتى تضغط «حفظ».",
            confirmLabel = "استعادة",
            onConfirm = {
                confirmDefaults = false
                viewModel.useDefaults()
            },
            onDismiss = { confirmDefaults = false },
        )
    }
}

@Composable
private fun PosterPreview(state: IdentityState, onDesign: (Int) -> Unit) {
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.62f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                val bitmap = state.preview
                if (bitmap != null) {
                    val image = remember(bitmap) { bitmap.asImageBitmap() }
                    Image(
                        bitmap = image,
                        contentDescription = "معاينة الملصق",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PosterDesigns.all.forEach { design ->
                    FilterChip(
                        selected = design.id == state.designId,
                        onClick = { onDesign(design.id) },
                        label = { Text(design.label) },
                    )
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
        modifier = Modifier.padding(top = 6.dp),
    )
}

@Composable
private fun TextField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    ltr: Boolean = false,
    keyboard: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    supporting: String? = null,
) {
    val style = if (ltr) LocalTextStyle.current.copy(textDirection = TextDirection.Ltr) else LocalTextStyle.current
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        textStyle = style,
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
        supportingText = if (supporting != null) ({ Text(supporting) }) else null,
    )
}

/** A colour typed as #rrggbb, shown as a swatch, with ready colours to tap. */
@Composable
private fun ColorField(label: String, value: String, presets: List<String>, onChange: (String) -> Unit) {
    val current = parseColor(value)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(current ?: MaterialTheme.colorScheme.surfaceContainerHigh)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            )
            Spacer(Modifier.width(12.dp))
            OutlinedTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f),
                label = { Text(label) },
                textStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                singleLine = true,
                isError = current == null,
            )
        }
        if (presets.isNotEmpty()) {
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { hex ->
                    val selected = BrandRules.normalizeHex(value) == hex
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(parseColor(hex) ?: Color.Gray)
                            .border(
                                border = BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant),
                                shape = CircleShape,
                            )
                            .clickable { onChange(hex) },
                    )
                }
            }
        }
    }
}

private fun parseColor(hex: String): Color? = BrandRules.argb(hex)?.let { Color(it) }
