package com.cady.cadymarketing.feature.studio

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.IconBadge
import com.cady.cadymarketing.core.components.LocalPicture
import com.cady.cadymarketing.core.components.Pill
import com.cady.cadymarketing.core.poster.PosterDesign
import com.cady.cadymarketing.core.poster.PosterDesigns
import com.cady.cadymarketing.core.theme.cady
import com.cady.cadymarketing.domain.engine.ArabicText
import com.cady.cadymarketing.domain.engine.CalendarMath
import com.cady.cadymarketing.domain.engine.DateUtil
import com.cady.cadymarketing.domain.model.CampaignStatus
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.CheckResult
import com.cady.cadymarketing.domain.model.Issue
import com.cady.cadymarketing.domain.model.IssueKind
import com.cady.cadymarketing.domain.model.Product
import com.cady.cadymarketing.domain.model.SavedContent
import com.cady.cadymarketing.domain.model.StockStatus
import java.util.Locale

/** Everything the panels can ask the screen to do. */
class StudioActions(
    val onToggleTool: (StudioTool) -> Unit,
    val onProduct: (String) -> Unit,
    val onChannel: (Channel) -> Unit,
    val onTone: (String) -> Unit,
    val onAngle: (String) -> Unit,
    val onGenerate: () -> Unit,
    val onIdeas: () -> Unit,
    val onUseIdea: (Int) -> Unit,
    val onAi: () -> Unit,
    val onGatewaySetup: () -> Unit,
    val onCustomerName: () -> Unit,
    val onCopy: () -> Unit,
    val onText: (String) -> Unit,
    val onFix: (Issue) -> Unit,
    val onDesign: (Int) -> Unit,
    val onPickPicture: () -> Unit,
    val onRemovePicture: () -> Unit,
    val onPickDate: () -> Unit,
    val onPickTime: () -> Unit,
    val onSetDate: (String) -> Unit,
    val onClearSchedule: () -> Unit,
    val onCampaign: (String?) -> Unit,
    val onNewCampaign: () -> Unit,
    val onOpenSaved: (SavedContent) -> Unit,
    val onShowSaved: () -> Unit,
    val loadThumb: suspend (String) -> Bitmap?,
)

/**
 * The open tool's panel, or the review (check result and recent saves) when no tool is open. One card with a header,
 * so it reads as the single place the post is edited; switching tools fades one panel into the next. The preview
 * above it never moves.
 */
@Composable
fun ToolPanel(state: StudioState, actions: StudioActions, modifier: Modifier = Modifier) {
    CadyCard(modifier = modifier) {
        Crossfade(
            targetState = state.openTool,
            modifier = Modifier.fillMaxSize(),
            animationSpec = tween(durationMillis = 180),
            label = "studioPanel",
        ) { tool ->
            when (tool) {
                null -> OverviewPanel(state, actions)
                StudioTool.PRODUCT -> ProductPanel(state, actions)
                StudioTool.CHANNEL -> ChannelPanel(state, actions)
                StudioTool.TEXT -> TextPanel(state, actions)
                StudioTool.DESIGN -> DesignPanel(state, actions)
                StudioTool.PICTURE -> PicturePanel(state, actions)
                StudioTool.SCHEDULE -> SchedulePanel(state, actions)
            }
        }
    }
}

/**
 * The frame of every panel: the header, then the body. A body scrolls, except the product panel's, whose grid scrolls by
 * itself. The body has no side padding: each block pads itself, so a row of chips can scroll right up to the card's edge.
 */
@Composable
private fun PanelScaffold(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onCollapse: (() -> Unit)?,
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        ToolHeader(icon = icon, title = title, subtitle = subtitle, onCollapse = onCollapse)
        if (scrollable) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
                content = content,
            )
        } else {
            Column(modifier = Modifier.weight(1f).fillMaxWidth(), content = content)
        }
    }
}

/** A row of choices that scrolls sideways (tones, angles, campaigns). */
@Composable
private fun ChipRow(items: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PanelPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items.forEach { item ->
            FilterChip(selected = item == selected, onClick = { onSelect(item) }, label = { Text(item) })
        }
    }
}

// ---------------------------------------------------------------------- review (no tool open)

@Composable
private fun OverviewPanel(state: StudioState, actions: StudioActions) {
    PanelScaffold(
        icon = Icons.Filled.Tune,
        title = "مراجعة المنشور",
        subtitle = "نتيجة الفحص وآخر المحفوظات",
        onCollapse = null,
    ) {
        PanelLabel("نتيجة الفحص")
        IssuesList(state.check, state.currentText.isNotBlank(), actions.onFix)
        PanelLabel("آخر المحفوظات")
        RecentSaved(state.saved, actions)
        PanelHint("اختر أداة من الشريط السفلي لتعديل المنشور.")
    }
}

@Composable
private fun RecentSaved(saved: List<SavedContent>, actions: StudioActions) {
    if (saved.isEmpty()) {
        PanelHint("لا يوجد محتوى محفوظ بعد. اضغط على أيقونة الحفظ في الأعلى لحفظ مسودة.")
        return
    }
    Column(
        modifier = Modifier.padding(horizontal = PanelPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        saved.take(RECENT_COUNT).forEach { item ->
            SavedPostCard(item = item, onClick = { actions.onOpenSaved(item) })
        }
        // Always offered: the list is also where a saved post is deleted.
        TextButton(onClick = actions.onShowSaved, modifier = Modifier.align(Alignment.End)) {
            Text("كل المحفوظات")
        }
    }
}

// ---------------------------------------------------------------------- check results

@Composable
private fun IssuesList(check: CheckResult, hasText: Boolean, onFix: (Issue) -> Unit) {
    if (!hasText) {
        PanelHint("اكتب نصًا أو اختر صيغة ليُفحص.")
        return
    }
    if (check.issues.isEmpty()) {
        Row(
            modifier = Modifier.padding(horizontal = PanelPadding, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.cady.success)
            Spacer(Modifier.width(8.dp))
            Text("اجتاز فحص الادعاءات والأرقام", color = MaterialTheme.cady.success)
        }
        return
    }
    Column(
        modifier = Modifier.padding(horizontal = PanelPadding),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        check.errors.distinctBy { it.message }.forEach { IssueCard(it, true, onFix) }
        check.warnings.distinctBy { it.message }.forEach { IssueCard(it, false, onFix) }
    }
}

/** One note of the check: red when it blocks approval, amber when it is only advice, with the fix beside it when there is one. */
@Composable
private fun IssueCard(issue: Issue, isError: Boolean, onFix: (Issue) -> Unit) {
    val tone = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.cady.warning
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tone.copy(alpha = 0.10f))
            .border(1.dp, tone.copy(alpha = 0.22f), shape)
            .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isError) Icons.Filled.Warning else Icons.Filled.Info,
            contentDescription = null,
            tint = tone,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = issue.message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 8.dp),
        )
        if (issue.match != null && issue.kind != IssueKind.UNKNOWN_PLACEHOLDER) {
            TextButton(onClick = { onFix(issue) }) { Text("احذفها") }
        }
    }
}

// ---------------------------------------------------------------------- product

@Composable
private fun ProductPanel(state: StudioState, actions: StudioActions) {
    var query by rememberSaveable { mutableStateOf("") }
    val key = remember(query) { searchKey(query) }
    val shown = remember(state.products, key) {
        if (key.isEmpty()) state.products else state.products.filter { searchKey(it.name).contains(key) }
    }
    val gridState = rememberLazyGridState()
    // Opens with the chosen product in view, not at the top of a long list.
    LaunchedEffect(Unit) {
        val index = state.products.indexOfFirst { it.id == state.productId }
        if (index >= GRID_COLUMNS) gridState.scrollToItem(index)
    }
    PanelScaffold(
        icon = Icons.Filled.Inventory2,
        title = "اختيار الصنف",
        subtitle = "الأسماء والأسعار والعروض تُقرأ من كادي",
        onCollapse = { actions.onToggleTool(StudioTool.PRODUCT) },
        scrollable = false,
    ) {
        if (state.products.size > SEARCH_FROM) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PanelPadding, vertical = 4.dp),
                singleLine = true,
                placeholder = { Text("ابحث عن صنف") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "مسح البحث")
                        }
                    }
                } else {
                    null
                },
                shape = RoundedCornerShape(16.dp),
            )
        }
        if (shown.isEmpty()) {
            PanelHint("لا يوجد صنف بهذا الاسم.")
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(GRID_COLUMNS),
                state = gridState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(start = PanelPadding, end = PanelPadding, top = 8.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(shown, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        selected = product.id == state.productId,
                        stock = state.stock[product.id]?.status,
                        loader = actions.loadThumb,
                        onClick = { actions.onProduct(product.id) },
                    )
                }
            }
        }
    }
}

/** A product: its picture, name and price, with one small tag (out of stock, low, surplus, or on offer) and a tick when chosen. */
@Composable
private fun ProductCard(
    product: Product,
    selected: Boolean,
    stock: StockStatus?,
    loader: suspend (String) -> Bitmap?,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.cady
    val tag: Pair<String, Color>? = when {
        stock == StockStatus.OUT -> StockStatus.OUT.label to scheme.error
        stock == StockStatus.LOW -> StockStatus.LOW.label to extra.warning
        stock == StockStatus.OVER -> StockStatus.OVER.label to extra.info
        product.hasOffer -> "عرض" to extra.warning
        else -> null
    }
    CadyCard(onClick = onClick, selected = selected, modifier = Modifier.fillMaxWidth()) {
        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                LocalPicture(
                    fileName = product.picturePath,
                    loader = loader,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(14.dp)),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.labelMedium,
                    minLines = 2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = ArabicText.formatNumber(product.effectivePrice),
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.primary,
                )
                Spacer(Modifier.height(2.dp))
                if (tag != null) {
                    Pill(text = tag.first, background = tag.second.copy(alpha = 0.16f), foreground = tag.second)
                } else {
                    // Keeps every card the same height, tagged or not.
                    Spacer(Modifier.height(TAG_SLOT))
                }
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "الصنف المختار",
                    tint = scheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .size(18.dp),
                )
            }
        }
    }
}

// ---------------------------------------------------------------------- channel

@Composable
private fun ChannelPanel(state: StudioState, actions: StudioActions) {
    PanelScaffold(
        icon = Icons.Filled.Campaign,
        title = "القناة",
        subtitle = "يتغير مقاس الصورة والنص والرابط معها",
        onCollapse = { actions.onToggleTool(StudioTool.CHANNEL) },
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = PanelPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Channel.entries.forEach { channel ->
                ChannelCard(channel = channel, selected = channel == state.channel) { actions.onChannel(channel) }
            }
        }
        PanelHint(
            if (state.channel.isWhatsApp) {
                "الرابط في النص يفتح محادثة واتساب مع المتجر."
            } else {
                "الرابط في النص يفتح موقع المتجر ومعه رمز الحملة."
            },
        )
    }
}

/** A channel: a small frame in the shape of its poster, its name, its size and the length its text suits. */
@Composable
private fun ChannelCard(channel: Channel, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val frameShape = RoundedCornerShape(6.dp)
    CadyCard(onClick = onClick, selected = selected, modifier = Modifier.width(116.dp)) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.height(60.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .height(56.dp)
                        .aspectRatio(channel.aspect)
                        .clip(frameShape)
                        .background(scheme.primary.copy(alpha = if (selected) 0.20f else 0.08f))
                        .border(1.5.dp, if (selected) scheme.primary else scheme.outline, frameShape),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = channel.label,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = ltr(channel.sizeLabel),
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
            Text(
                text = "حتى ${channel.softLimit} حرف",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

// ---------------------------------------------------------------------- text

@Composable
private fun TextPanel(state: StudioState, actions: StudioActions) {
    val over = state.currentText.length > state.channel.softLimit
    PanelScaffold(
        icon = Icons.Filled.Edit,
        title = "نص المنشور",
        subtitle = "النبرة والزاوية ثم الصيغ والتعديل",
        onCollapse = { actions.onToggleTool(StudioTool.TEXT) },
    ) {
        PanelLabel("النبرة")
        ChipRow(items = state.tones, selected = state.tone, onSelect = actions.onTone)
        PanelLabel("الزاوية")
        ChipRow(items = state.angles, selected = state.angle, onSelect = actions.onAngle)

        Column(
            modifier = Modifier.padding(horizontal = PanelPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = actions.onGenerate, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("ولّد صيغًا جديدة")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolActionButton(
                    icon = Icons.Filled.Lightbulb,
                    label = "أفكار",
                    onClick = actions.onIdeas,
                    modifier = Modifier.weight(1f),
                    enabled = !state.aiBusy,
                )
                ToolActionButton(
                    icon = Icons.Filled.AutoAwesome,
                    label = "ذكاء",
                    onClick = actions.onAi,
                    modifier = Modifier.weight(1f),
                    enabled = !state.aiBusy,
                    busy = state.aiBusy,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolActionButton(
                    icon = Icons.Filled.PersonAdd,
                    label = "اسم العميل",
                    onClick = actions.onCustomerName,
                    modifier = Modifier.weight(1f),
                )
                ToolActionButton(
                    icon = Icons.Filled.ContentCopy,
                    label = "نسخ النص",
                    onClick = actions.onCopy,
                    modifier = Modifier.weight(1f),
                )
            }
            TextButton(onClick = actions.onGatewaySetup, modifier = Modifier.align(Alignment.Start)) {
                Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (state.gateway.isConfigured) "إعدادات بوابة الذكاء" else "ربط الذكاء: الصق رابط البوابة")
            }
        }

        if (state.ideas.isNotEmpty()) {
            PanelLabel("أفكار جاهزة")
            Column(
                modifier = Modifier.padding(horizontal = PanelPadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.ideas.forEachIndexed { index, idea ->
                    CadyCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
                        ) {
                            Text(idea, style = MaterialTheme.typography.bodyMedium)
                            TextButton(
                                onClick = { actions.onUseIdea(index) },
                                modifier = Modifier.align(Alignment.End),
                            ) { Text("اكتب منشورًا بهذه الفكرة") }
                        }
                    }
                }
                TextButton(onClick = actions.onIdeas, modifier = Modifier.align(Alignment.End)) { Text("فكرة أخرى") }
            }
        }

        PanelLabel("النص")
        OutlinedTextField(
            value = state.currentText,
            onValueChange = actions.onText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = PanelPadding),
            textStyle = LocalTextStyle.current,
            minLines = 4,
            maxLines = 10,
            shape = RoundedCornerShape(18.dp),
            supportingText = {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (over) {
                        Text(
                            text = "أطول من المعتاد لهذه القناة",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.cady.warning,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    CharCounter(length = state.currentText.length, limit = state.channel.softLimit)
                }
            },
        )
        Spacer(Modifier.height(10.dp))
        IssuesList(state.check, state.currentText.isNotBlank(), actions.onFix)
    }
}

// ---------------------------------------------------------------------- design

@Composable
private fun DesignPanel(state: StudioState, actions: StudioActions) {
    val aspect = state.channel.aspect
    // A tall poster (a story) gets narrower thumbnails, so the row never grows taller than the panel.
    val thumbWidth = minOf(THUMB_MAX_WIDTH, THUMB_MAX_HEIGHT * aspect)
    PanelScaffold(
        icon = Icons.Filled.Palette,
        title = "التصميم",
        subtitle = "تتغير المعاينة فور الاختيار",
        onCollapse = { actions.onToggleTool(StudioTool.DESIGN) },
    ) {
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = PanelPadding, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PosterDesigns.all.forEach { design ->
                DesignThumb(
                    design = design,
                    selected = design.id == state.designId,
                    thumb = state.thumbs[design.id],
                    aspect = aspect,
                    width = thumbWidth,
                    onClick = { actions.onDesign(design.id) },
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = PanelPadding, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Filled.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = "هذه الأيقونة تعني أن التصميم يعرض الصورة المختارة",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DesignThumb(
    design: PosterDesign,
    selected: Boolean,
    thumb: Bitmap?,
    aspect: Float,
    width: Dp,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val scale by animateFloatAsState(targetValue = if (selected) 1f else 0.96f, label = "designScale")
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .width(width)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(role = Role.RadioButton, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(aspect)
                .clip(shape)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) scheme.primary else scheme.outlineVariant,
                    shape = shape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null) {
                val image = remember(thumb) { thumb.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = design.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            if (design.usesPicture) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = "يعرض الصورة المختارة",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(4.dp),
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "التصميم المختار",
                    tint = scheme.primary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
        Text(
            text = design.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) scheme.primary else scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

// ---------------------------------------------------------------------- picture

@Composable
private fun PicturePanel(state: StudioState, actions: StudioActions) {
    val own = state.pictureFile != null
    val linked = state.product?.picturePath != null
    val shown = state.effectivePicture
    PanelScaffold(
        icon = Icons.Filled.PhotoLibrary,
        title = "الصورة",
        subtitle = "تظهر في بعض التصاميم فقط",
        onCollapse = { actions.onToggleTool(StudioTool.PICTURE) },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = PanelPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LocalPicture(
                fileName = shown,
                loader = actions.loadThumb,
                fallback = Icons.Filled.Image,
                modifier = Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(18.dp)),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = when {
                        own -> "صورة هذا المنشور"
                        linked -> "صورة الصنف"
                        else -> "لا توجد صورة"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = when {
                        own -> "اخترتها لهذا المنشور وحده."
                        linked -> "مرتبطة بالصنف. اختر صورة هنا لتخص هذا المنشور وحده."
                        else -> "اربط صورة بالصنف من «الأصناف والعروض» في «المزيد»، أو اختر واحدة هنا."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = PanelPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(onClick = actions.onPickPicture) {
                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (shown == null) "اختيار من المعرض" else "تغيير الصورة")
            }
            if (own) {
                OutlinedButton(onClick = actions.onRemovePicture) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("إزالة")
                }
            }
        }
        PanelLabel("تظهر في هذه التصاميم")
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = PanelPadding),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            PosterDesigns.all.filter { it.usesPicture }.forEach { design -> Pill(text = design.label) }
        }
        PanelHint("تُنسخ الصورة داخل التطبيق، فيبقى المنشور قابلًا لإعادة الرسم حتى لو حُذفت من المعرض.")
    }
}

// ---------------------------------------------------------------------- schedule

@Composable
private fun SchedulePanel(state: StudioState, actions: StudioActions) {
    val schedule = state.scheduledAt
    val scheduledDate = schedule?.substringBefore('T')
    val today = remember { DateUtil.today() }
    val quickDays = remember(today) {
        listOf(
            "اليوم" to today,
            "غدًا" to CalendarMath.addDays(today, 1),
            "بعد يومين" to CalendarMath.addDays(today, 2),
        )
    }
    PanelScaffold(
        icon = Icons.Filled.CalendarMonth,
        title = "الحملة والموعد",
        subtitle = "ربط المنشور بحملة وتحديد وقت نشره",
        onCollapse = { actions.onToggleTool(StudioTool.SCHEDULE) },
    ) {
        PanelLabel("الحملة")
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = PanelPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.campaignId == null,
                onClick = { actions.onCampaign(null) },
                label = { Text("بلا حملة") },
            )
            state.campaigns
                .filter { it.status != CampaignStatus.ENDED || it.id == state.campaignId }
                .forEach { campaign ->
                    FilterChip(
                        selected = campaign.id == state.campaignId,
                        onClick = { actions.onCampaign(campaign.id) },
                        label = { Text(campaign.name) },
                    )
                }
            AssistChip(
                onClick = actions.onNewCampaign,
                label = { Text("حملة جديدة") },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        val linked = state.campaign
        PanelHint(
            if (linked == null) {
                "اختر حملة لتحمل روابط المنشور رمزها، فيُنسب كل طلب إلى الحملة."
            } else {
                "رمز الحملة ${linked.code}: روابط النص تحمله، فيُنسب كل طلب يأتي منها إلى «${linked.name}»."
            },
        )

        PanelLabel("موعد النشر")
        CadyCard(modifier = Modifier.padding(horizontal = PanelPadding).fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconBadge(icon = Icons.Filled.CalendarMonth, size = 40.dp, iconSize = 20.dp)
                Column(modifier = Modifier.weight(1f)) {
                    if (schedule == null) {
                        Text("لم يُحدَّد موعد", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "اختر يومًا ووقتًا، أو اتركه بلا موعد.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = CalendarMath.longDate(schedule.substringBefore('T')),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            text = timeLabel(schedule.substringAfter('T', "")),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (schedule != null) {
                    IconButton(onClick = actions.onClearSchedule) {
                        Icon(Icons.Filled.Close, contentDescription = "مسح الموعد")
                    }
                }
            }
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = PanelPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            quickDays.forEach { (label, date) ->
                FilterChip(
                    selected = scheduledDate == date,
                    onClick = { actions.onSetDate(date) },
                    label = { Text(label) },
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = PanelPadding),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = actions.onPickDate, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("التاريخ")
            }
            OutlinedButton(onClick = actions.onPickTime, modifier = Modifier.weight(1f)) {
                Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("الوقت")
            }
        }
    }
}

/** "14:30" as "2:30 م": the time of day the way people say it, with ص and م. */
private fun timeLabel(time: String): String {
    val parts = time.split(':')
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: return time
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val hour12 = when {
        hour == 0 -> 12
        hour > 12 -> hour - 12
        else -> hour
    }
    return String.format(Locale.US, "%d:%02d %s", hour12, minute, if (hour < 12) "ص" else "م")
}

private const val RECENT_COUNT = 3
private const val GRID_COLUMNS = 3
private const val SEARCH_FROM = 6
private val TAG_SLOT = 22.dp
private val THUMB_MAX_WIDTH = 116.dp
private val THUMB_MAX_HEIGHT = 170.dp
