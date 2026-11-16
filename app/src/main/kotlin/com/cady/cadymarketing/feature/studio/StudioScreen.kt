package com.cady.cadymarketing.feature.studio

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.EmptyState
import com.cady.cadymarketing.core.theme.cady
import com.cady.cadymarketing.core.util.Clipboard
import com.cady.cadymarketing.domain.engine.ClaimChecker
import com.cady.cadymarketing.domain.model.CheckResult
import com.cady.cadymarketing.domain.model.Issue
import kotlinx.coroutines.launch

/**
 * The Studio: a full-screen workspace (no bottom bar). The live poster and text stay at the top, the open
 * tool's panel sits below them and never covers the preview, the six tools are at the very bottom.
 */
@Composable
fun StudioScreen(
    onClose: () -> Unit,
    onOpenProducts: () -> Unit = {},
    viewModel: StudioViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showSaved by remember { mutableStateOf(false) }
    var showGateway by remember { mutableStateOf(false) }
    var showClose by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }

    // While the keyboard is open the poster shrinks and the formulas row hides, so the text field keeps room.
    val keyboardOpen = WindowInsets.ime.getBottom(LocalDensity.current) > 0

    val say: (String) -> Unit = { text ->
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    val requestClose: () -> Unit = {
        if (state.dirty) {
            showClose = true
        } else {
            onClose()
        }
    }
    BackHandler(onBack = requestClose)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StudioEvent.Message -> say(event.text)
                is StudioEvent.CopyText -> {
                    Clipboard.copy(context, "نص المنشور", event.text)
                    say("نُسخ النص")
                }
                is StudioEvent.Share -> {
                    if (launchShare(context, event)) {
                        say("النص منسوخ أيضًا، الصقه إن لم يظهر مع الصورة")
                    } else {
                        say("التطبيق غير مثبّت على هذا الجهاز")
                    }
                }
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) viewModel.pickPicture(uri)
    }

    val actions = StudioActions(
        onProduct = viewModel::selectProduct,
        onChannel = viewModel::selectChannel,
        onTone = viewModel::selectTone,
        onAngle = viewModel::selectAngle,
        onGenerate = viewModel::regenerate,
        onIdeas = viewModel::requestIdeas,
        onUseIdea = viewModel::useIdea,
        onAi = {
            if (state.gateway.isConfigured) {
                viewModel.generateWithAi()
            } else {
                showGateway = true
            }
        },
        onGatewaySetup = { showGateway = true },
        onCustomerName = viewModel::addCustomerName,
        onCopy = viewModel::copyText,
        onText = viewModel::onTextChange,
        onFix = viewModel::fixIssue,
        onDesign = viewModel::selectDesign,
        onPickPicture = {
            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        onRemovePicture = viewModel::removePicture,
        onPickDate = { showDate = true },
        onPickTime = { showTime = true },
        onClearSchedule = viewModel::clearSchedule,
        loadThumb = viewModel::loadThumb,
    )

    Scaffold(
        modifier = Modifier.imePadding(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbar) },
        topBar = {
            StudioTopBar(
                productName = state.product?.name ?: "الاستوديو",
                hasText = state.currentText.isNotBlank(),
                canApprove = state.canApprove,
                onClose = requestClose,
                onShare = viewModel::share,
                onSaveImage = viewModel::saveImage,
                onCopy = viewModel::copyText,
                onHistory = { showSaved = true },
                onSave = { viewModel.save(false) },
                onApprove = { viewModel.save(true) },
            )
        },
        bottomBar = { StudioToolBar(openTool = state.openTool, onTool = viewModel::toggleTool) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.loading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.products.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Inventory2,
                    title = "لا توجد أصناف بعد",
                    message = "الاستوديو يكتب عن أصناف كادي. اقرأ الأصناف من كادي أولًا ثم ارجع.",
                    actionLabel = "فتح الأصناف والعروض",
                    onAction = onOpenProducts,
                )
            } else {
                PreviewRow(state, compact = keyboardOpen)
                if (!keyboardOpen) FormulaRow(state, viewModel::selectFormula)
                HorizontalDivider()
                Box(modifier = Modifier.weight(1f)) {
                    ToolPanel(state, actions)
                }
            }
        }
    }

    if (showSaved) {
        SavedContentDialog(
            saved = state.saved,
            onOpen = { content ->
                viewModel.loadSaved(content)
                showSaved = false
            },
            onDelete = viewModel::deleteSaved,
            onDismiss = { showSaved = false },
        )
    }
    if (showGateway) {
        GatewayDialog(
            config = state.gateway,
            testing = state.gatewayTesting,
            testMessage = state.gatewayTestMessage,
            onTest = viewModel::testGateway,
            onSave = { url, token ->
                viewModel.saveGateway(url, token)
                showGateway = false
            },
            onDismiss = { showGateway = false },
        )
    }
    if (showClose) {
        CloseConfirmDialog(
            onDiscard = {
                showClose = false
                onClose()
            },
            onKeep = { showClose = false },
        )
    }
    if (showDate) {
        DatePickerDialogHost(
            initialDate = state.scheduledAt?.substringBefore('T'),
            onConfirm = { date ->
                viewModel.setScheduleDate(date)
                showDate = false
            },
            onDismiss = { showDate = false },
        )
    }
    if (showTime) {
        TimePickerDialogHost(
            initialTime = state.scheduledAt?.substringAfter('T', ""),
            onConfirm = { hour, minute ->
                viewModel.setScheduleTime(hour, minute)
                showTime = false
            },
            onDismiss = { showTime = false },
        )
    }
}

// ---------------------------------------------------------------------- top bar

@Composable
private fun StudioTopBar(
    productName: String,
    hasText: Boolean,
    canApprove: Boolean,
    onClose: () -> Unit,
    onShare: (ShareTarget) -> Unit,
    onSaveImage: () -> Unit,
    onCopy: () -> Unit,
    onHistory: () -> Unit,
    onSave: () -> Unit,
    onApprove: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Surface(color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "إغلاق")
            }
            Text(
                text = productName,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.Share, contentDescription = "مشاركة")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("مشاركة عبر واتساب") },
                        onClick = {
                            menuOpen = false
                            onShare(ShareTarget.WHATSAPP)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("مشاركة عبر واتساب للأعمال") },
                        onClick = {
                            menuOpen = false
                            onShare(ShareTarget.WHATSAPP_BUSINESS)
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("مشاركة عبر تطبيق آخر") },
                        onClick = {
                            menuOpen = false
                            onShare(ShareTarget.OTHER)
                        },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("حفظ الصورة في csdy") },
                        onClick = {
                            menuOpen = false
                            onSaveImage()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("نسخ النص") },
                        onClick = {
                            menuOpen = false
                            onCopy()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("المحتوى المحفوظ") },
                        onClick = {
                            menuOpen = false
                            onHistory()
                        },
                    )
                }
            }
            TextButton(onClick = onSave, enabled = hasText) { Text("حفظ") }
            Button(
                onClick = onApprove,
                enabled = canApprove,
                contentPadding = PaddingValues(horizontal = 14.dp),
            ) { Text("اعتماد") }
        }
    }
}

// ---------------------------------------------------------------------- preview and text

@Composable
private fun PreviewRow(state: StudioState, compact: Boolean) {
    val configuration = LocalConfiguration.current
    val posterHeight = configuration.screenHeightDp.dp * (if (compact) 0.14f else 0.26f)
    val posterWidth = minOf(posterHeight * state.channel.aspect, configuration.screenWidthDp.dp * 0.46f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PosterBox(bitmap = state.preview, aspect = state.channel.aspect, modifier = Modifier.width(posterWidth))
        TextSummary(state = state, modifier = Modifier.weight(1f).height(posterHeight))
    }
}

@Composable
private fun PosterBox(bitmap: Bitmap?, aspect: Float, modifier: Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(aspect)
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        contentAlignment = Alignment.Center,
    ) {
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
}

@Composable
private fun TextSummary(state: StudioState, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = scheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, scheme.outlineVariant),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            CheckBadge(check = state.check, hasText = state.currentText.isNotBlank())
            Spacer(Modifier.height(6.dp))
            Text(
                text = rememberHighlighted(state.currentText, state.check.errors),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun CheckBadge(check: CheckResult, hasText: Boolean) {
    if (!hasText) {
        Text("لا يوجد نص", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (check.passed) {
        val advice = check.warnings.distinctBy { it.message }.size
        Text(
            text = if (advice == 0) "✓ اجتاز الفحص" else "✓ اجتاز الفحص · $advice نصيحة",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.cady.success,
        )
    } else {
        val count = check.errors.distinctBy { it.message }.size
        Text(
            text = "⚠ $count ملاحظة",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

/** The text with the offending parts (banned phrases, numbers not in the catalogue) painted. */
@Composable
private fun rememberHighlighted(text: String, errors: List<Issue>): AnnotatedString {
    val background = MaterialTheme.colorScheme.errorContainer
    val foreground = MaterialTheme.colorScheme.onErrorContainer
    return remember(text, errors, background, foreground) {
        val ranges = ClaimChecker.highlights(text, errors)
        buildAnnotatedString {
            append(text)
            for (range in ranges) {
                addStyle(SpanStyle(background = background, color = foreground), range.first, range.last + 1)
            }
        }
    }
}

@Composable
private fun FormulaRow(state: StudioState, onSelect: (Int) -> Unit) {
    if (state.formulas.isEmpty()) return
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        itemsIndexed(state.formulas) { index, formula ->
            FilterChip(
                selected = index == state.formulaIndex,
                onClick = { onSelect(index) },
                label = { Text(formula.name) },
            )
        }
    }
}

// ---------------------------------------------------------------------- tool bar

private class ToolDef(val tool: StudioTool, val label: String, val icon: ImageVector)

private val toolDefs = listOf(
    ToolDef(StudioTool.PRODUCT, "الصنف", Icons.Filled.Inventory2),
    ToolDef(StudioTool.CHANNEL, "القناة", Icons.Filled.Campaign),
    ToolDef(StudioTool.TEXT, "النص", Icons.Filled.Edit),
    ToolDef(StudioTool.DESIGN, "التصميم", Icons.Filled.Palette),
    ToolDef(StudioTool.PICTURE, "الصورة", Icons.Filled.PhotoLibrary),
    ToolDef(StudioTool.SCHEDULE, "الموعد", Icons.Filled.CalendarMonth),
)

@Composable
private fun StudioToolBar(openTool: StudioTool?, onTool: (StudioTool) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 2.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(vertical = 4.dp),
        ) {
            toolDefs.forEach { def ->
                val selected = openTool == def.tool
                val color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onTool(def.tool) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        modifier = Modifier
                            .width(28.dp)
                            .height(3.dp)
                            .background(
                                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainer,
                                shape = RoundedCornerShape(2.dp),
                            ),
                    )
                    Spacer(Modifier.height(4.dp))
                    Icon(def.icon, contentDescription = null, tint = color)
                    Text(def.label, style = MaterialTheme.typography.labelSmall, color = color, maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------- sharing

/** Opens the share target with the poster and the text. The text is also copied, as WhatsApp may drop it. */
private fun launchShare(context: Context, event: StudioEvent.Share): Boolean {
    Clipboard.copy(context, "نص المنشور", event.text)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/png"
        putExtra(Intent.EXTRA_STREAM, event.uri)
        putExtra(Intent.EXTRA_TEXT, event.text)
        clipData = ClipData.newRawUri("", event.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        event.packageName?.let { setPackage(it) }
    }
    return try {
        context.startActivity(
            if (event.packageName == null) Intent.createChooser(intent, "مشاركة المنشور") else intent,
        )
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
