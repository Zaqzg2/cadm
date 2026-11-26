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
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.EmptyState
import com.cady.cadymarketing.core.role.LocalRole
import com.cady.cadymarketing.core.theme.LocalCadyStyle
import com.cady.cadymarketing.core.theme.barLook
import com.cady.cadymarketing.core.theme.cady
import com.cady.cadymarketing.core.theme.glassPanel
import com.cady.cadymarketing.core.theme.topBarLook
import com.cady.cadymarketing.core.util.Clipboard
import com.cady.cadymarketing.domain.engine.ClaimChecker
import com.cady.cadymarketing.domain.engine.ContentFlow
import com.cady.cadymarketing.domain.engine.NoteLevel
import com.cady.cadymarketing.domain.engine.StockNote
import com.cady.cadymarketing.domain.model.AppStyle
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.Issue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** How long "save and exit" waits for the save to finish before it gives up and leaves the screen open. */
private const val EXIT_SAVE_TIMEOUT_MS = 5_000L

/**
 * The Studio: a full-screen workspace. The live poster and its text stay at the top, the open tool's panel sits below
 * them and never covers the preview, and the six tools float at the bottom like the app's own bar.
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
    var showShare by remember { mutableStateOf(false) }
    var showPreview by remember { mutableStateOf(false) }
    var showGateway by remember { mutableStateOf(false) }
    var showClose by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var showTime by remember { mutableStateOf(false) }
    var showNewCampaign by remember { mutableStateOf(false) }
    var exitAfterSave by remember { mutableStateOf(false) }

    // While the keyboard is open the poster shrinks, the formulas row hides and the tool bar keeps only its icons,
    // so the text field keeps room.
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

    // "Save and exit" leaves once the draft is no longer dirty. When it cannot be saved (no text, a failed write) the
    // screen stays open and the message says why.
    LaunchedEffect(exitAfterSave) {
        if (exitAfterSave) {
            val saved = withTimeoutOrNull(EXIT_SAVE_TIMEOUT_MS) { snapshotFlow { state.dirty }.first { !it } }
            exitAfterSave = false
            if (saved != null) onClose()
        }
    }

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
        onToggleTool = viewModel::toggleTool,
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
        onSetDate = viewModel::setScheduleDate,
        onClearSchedule = viewModel::clearSchedule,
        onCampaign = viewModel::selectCampaign,
        onNewCampaign = { showNewCampaign = true },
        onOpenSaved = viewModel::loadSaved,
        onShowSaved = { showSaved = true },
        loadThumb = viewModel::loadThumb,
    )

    Scaffold(
        modifier = Modifier.imePadding(),
        // See-through: the page is drawn once behind the whole app by the theme.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(hostState = snackbar) },
        topBar = {
            StudioTopBar(
                title = state.product?.name ?: "الاستوديو",
                subtitle = topBarSubtitle(state),
                hasText = state.currentText.isNotBlank(),
                canApprove = state.canApprove,
                submitLabel = ContentFlow.submitLabel(LocalRole.current),
                onClose = requestClose,
                onSave = { viewModel.save(false) },
                onShare = { showShare = true },
                onApprove = { viewModel.save(true) },
            )
        },
        bottomBar = {
            if (!state.loading && state.products.isNotEmpty()) {
                StudioToolBar(state = state, compact = keyboardOpen, onTool = viewModel::toggleTool)
            }
        },
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
                if (!keyboardOpen) state.stockNote?.let { StockBanner(it) }
                PreviewRow(
                    state = state,
                    compact = keyboardOpen,
                    onEnlarge = { showPreview = true },
                    onWrite = {
                        if (state.openTool != StudioTool.TEXT) viewModel.toggleTool(StudioTool.TEXT)
                    },
                )
                if (!keyboardOpen) FormulaRow(state, viewModel::selectFormula)
                ToolPanel(
                    state = state,
                    actions = actions,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 4.dp),
                )
            }
        }
    }

    if (showShare) {
        ShareSheet(
            onDismiss = { showShare = false },
            onShare = viewModel::share,
            onSaveImage = viewModel::saveImage,
            onCopy = viewModel::copyText,
        )
    }
    if (showPreview) {
        PreviewDialog(
            channel = state.channel,
            small = state.preview,
            loadLarge = viewModel::renderLargePreview,
            onDismiss = { showPreview = false },
        )
    }
    if (showSaved) {
        SavedContentSheet(
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
            canSave = state.currentText.isNotBlank(),
            onSave = {
                showClose = false
                viewModel.save(false)
                exitAfterSave = true
            },
            onDiscard = {
                showClose = false
                onClose()
            },
            onKeep = { showClose = false },
        )
    }
    if (showNewCampaign) {
        NewCampaignDialog(
            onCreate = { name ->
                viewModel.createCampaign(name)
                showNewCampaign = false
            },
            onDismiss = { showNewCampaign = false },
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

/** The channel the post is for and whether the work is saved: "واتساب (بث) · لم يُحفظ". */
private fun topBarSubtitle(state: StudioState): String {
    val parts = mutableListOf(state.channel.label)
    when {
        state.dirty -> parts += "لم يُحفظ"
        state.editingId != null -> parts += "محفوظ"
    }
    return parts.joinToString(" · ")
}

/**
 * Leave, what this is, then the three things you do with it: save a draft, share it, send it on. Sharing opens a
 * sheet, so the bar stays as light as it is.
 */
@Composable
private fun StudioTopBar(
    title: String,
    subtitle: String,
    hasText: Boolean,
    canApprove: Boolean,
    submitLabel: String,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onApprove: () -> Unit,
) {
    Surface(
        color = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.topBarLook(LocalCadyStyle.current),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(60.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "إغلاق")
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onSave, enabled = hasText) {
                Icon(Icons.Filled.Save, contentDescription = "حفظ كمسودة")
            }
            IconButton(onClick = onShare, enabled = hasText) {
                Icon(Icons.Filled.Share, contentDescription = "مشاركة")
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = onApprove,
                enabled = canApprove,
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) { Text(submitLabel) }
            Spacer(Modifier.width(4.dp))
        }
    }
}

// ---------------------------------------------------------------------- stock, preview and text

/** One line above the preview about the product's stock: a warning when it ran out or is low, good news for a surplus. */
@Composable
private fun StockBanner(note: StockNote) {
    val warning = note.level == NoteLevel.WARNING
    val scheme = MaterialTheme.colorScheme
    val container = if (warning) scheme.errorContainer else scheme.secondaryContainer
    val content = if (warning) scheme.onErrorContainer else scheme.onSecondaryContainer
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 4.dp)
            .clip(shape)
            .background(container.copy(alpha = 0.92f))
            .border(1.dp, content.copy(alpha = 0.18f), shape)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (warning) Icons.Filled.Warning else Icons.Filled.Info,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = note.text,
            style = MaterialTheme.typography.bodySmall,
            color = content,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * The poster and the text it carries, side by side. The poster is the one big thing on the screen: a tap opens it
 * large. The text card opens the text tool, so what you read is what you edit.
 */
@Composable
private fun PreviewRow(state: StudioState, compact: Boolean, onEnlarge: () -> Unit, onWrite: () -> Unit) {
    val configuration = LocalConfiguration.current
    val aspect = state.channel.aspect
    val frame = 5.dp
    val maxHeight = configuration.screenHeightDp.dp * (if (compact) 0.15f else 0.30f)
    val innerWidth = minOf(
        (maxHeight - frame * 2) * aspect,
        configuration.screenWidthDp.dp * 0.44f - frame * 2,
    )
    val cardHeight = innerWidth / aspect + frame * 2
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PosterCard(
            bitmap = state.preview,
            channel = state.channel,
            innerWidth = innerWidth,
            frame = frame,
            onClick = onEnlarge,
        )
        TextCard(
            state = state,
            onClick = onWrite,
            modifier = Modifier
                .weight(1f)
                .height(cardHeight),
        )
    }
}

@Composable
private fun PosterCard(bitmap: Bitmap?, channel: Channel, innerWidth: Dp, frame: Dp, onClick: () -> Unit) {
    val look = LocalCadyStyle.current
    val corner = maxOf(minOf(look.cardCorner - frame, 16.dp), 4.dp)
    CadyCard(onClick = onClick, modifier = Modifier.width(innerWidth + frame * 2)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(frame)
                .aspectRatio(channel.aspect)
                .clip(RoundedCornerShape(corner))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center,
        ) {
            if (bitmap != null) {
                val image = remember(bitmap) { bitmap.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = "معاينة الملصق، اضغط لتكبيرها",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
            Text(
                text = ltr(channel.sizeLabel),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 7.dp, vertical = 2.dp),
            )
            Icon(
                imageVector = Icons.Filled.OpenInFull,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(5.dp),
            )
        }
    }
}

@Composable
private fun TextCard(state: StudioState, onClick: () -> Unit, modifier: Modifier) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val hasText = state.currentText.isNotBlank()
    CadyCard(onClick = onClick, modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckChip(check = state.check, hasText = hasText, modifier = Modifier.weight(1f, fill = false))
                if (hasText) {
                    Spacer(Modifier.width(8.dp))
                    CharCounter(length = state.currentText.length, limit = state.channel.softLimit)
                }
            }
            Spacer(Modifier.height(8.dp))
            if (hasText) {
                Text(
                    text = rememberHighlighted(state.currentText, state.check.errors),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                )
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "اضغط لكتابة نص المنشور أو اختيار صيغة",
                        style = MaterialTheme.typography.bodySmall,
                        color = muted,
                    )
                }
            }
        }
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

/** The versions of the text (the direct one, the AI's, the ideas'): pick one and the poster and text follow. */
@Composable
private fun FormulaRow(state: StudioState, onSelect: (Int) -> Unit) {
    if (state.formulas.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "الصيغ",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyRow(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
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

/** A dot on a tool: [color] says how much it matters, [note] says it to a screen reader. */
private class ToolBadge(val color: Color, val note: String)

/** What a tool wants you to know without opening it: the text has notes, the picture is this post's own, a schedule is set. */
@Composable
private fun toolBadge(tool: StudioTool, state: StudioState): ToolBadge? {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.cady
    return when (tool) {
        StudioTool.TEXT -> when {
            state.currentText.isBlank() -> null
            state.check.errors.isNotEmpty() -> ToolBadge(scheme.error, "فيه ملاحظات تمنع الاعتماد")
            state.check.warnings.isNotEmpty() -> ToolBadge(extra.warning, "فيه نصائح")
            else -> null
        }
        StudioTool.PICTURE ->
            if (state.pictureFile != null) ToolBadge(extra.info, "صورة خاصة بهذا المنشور") else null
        StudioTool.SCHEDULE ->
            if (state.scheduledAt != null || state.campaignId != null) ToolBadge(extra.success, "فيه حملة أو موعد") else null
        else -> null
    }
}

/**
 * The six tools. In the glass look this is the same floating pill as the app's own bar, so the Studio does not feel like
 * another app; in the other looks it is the flat bar of that look. With the keyboard open it keeps only the icons.
 */
@Composable
private fun StudioToolBar(state: StudioState, compact: Boolean, onTool: (StudioTool) -> Unit) {
    val look = LocalCadyStyle.current
    val items: @Composable RowScope.() -> Unit = {
        toolDefs.forEach { def ->
            ToolItem(
                def = def,
                selected = state.openTool == def.tool,
                badge = toolBadge(def.tool, state),
                compact = compact,
                onClick = { onTool(def.tool) },
                modifier = Modifier.weight(1f),
            )
        }
    }
    if (look.barFloating) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = if (compact) 6.dp else 10.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassPanel(look, corner = 30.dp, brush = look.barBrush, border = look.barBorder)
                    .padding(horizontal = 6.dp, vertical = 6.dp)
                    .selectableGroup(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
                content = items,
            )
        }
    } else {
        Surface(color = look.barContainer, tonalElevation = 2.dp, modifier = Modifier.barLook(look)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(vertical = 4.dp)
                    .selectableGroup(),
                content = items,
            )
        }
    }
}

@Composable
private fun ToolItem(
    def: ToolDef,
    selected: Boolean,
    badge: ToolBadge?,
    compact: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val look = LocalCadyStyle.current
    val scheme = look.colorScheme
    val haptic = LocalHapticFeedback.current
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMedium),
        label = "toolPress",
    )
    val pill by animateColorAsState(
        targetValue = if (selected) look.barIndicator else Color.Transparent,
        animationSpec = tween(220),
        label = "toolPill",
    )
    val selectedTint = when {
        look.barSelected != Color.Unspecified -> look.barSelected
        look.style == AppStyle.CLASSIC -> scheme.onSecondaryContainer
        else -> scheme.onPrimaryContainer
    }
    Column(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(22.dp))
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            )
            .semantics { badge?.let { stateDescription = it.note } }
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(56.dp)
                .height(if (compact) 34.dp else 30.dp)
                .clip(CircleShape)
                .background(pill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = def.icon,
                contentDescription = if (compact) def.label else null,
                tint = if (selected) selectedTint else scheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp),
            )
            if (badge != null) {
                StatusDot(
                    color = badge.color,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 3.dp, end = 10.dp),
                )
            }
        }
        if (!compact) {
            Text(
                text = def.label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) scheme.onSurface else scheme.onSurfaceVariant,
                maxLines = 1,
            )
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
