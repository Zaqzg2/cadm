package com.cady.cadymarketing.feature.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.LocalPicture
import com.cady.cadymarketing.core.poster.PosterDesigns
import com.cady.cadymarketing.core.theme.cady
import com.cady.cadymarketing.domain.engine.ArabicText
import com.cady.cadymarketing.domain.model.CampaignStatus
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.CheckResult
import com.cady.cadymarketing.domain.model.Issue
import com.cady.cadymarketing.domain.model.IssueKind
import com.cady.cadymarketing.domain.model.Product

/** Everything the panels can ask the screen to do. */
class StudioActions(
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
    val onClearSchedule: () -> Unit,
    val onCampaign: (String?) -> Unit,
    val onNewCampaign: () -> Unit,
    val loadThumb: suspend (String) -> android.graphics.Bitmap?,
)

/** The open tool's panel, or the check results when no tool is open. It scrolls; the preview above never moves. */
@Composable
fun ToolPanel(state: StudioState, actions: StudioActions) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 12.dp),
    ) {
        when (state.openTool) {
            null -> CheckPanel(state, actions)
            StudioTool.PRODUCT -> ProductPanel(state, actions)
            StudioTool.CHANNEL -> ChannelPanel(state, actions)
            StudioTool.TEXT -> TextPanel(state, actions)
            StudioTool.DESIGN -> DesignPanel(state, actions)
            StudioTool.PICTURE -> PicturePanel(state, actions)
            StudioTool.SCHEDULE -> SchedulePanel(state, actions)
        }
    }
}

// ---------------------------------------------------------------------- small pieces

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
    )
}

// ---------------------------------------------------------------------- check results

@Composable
private fun CheckPanel(state: StudioState, actions: StudioActions) {
    SectionLabel("نتيجة الفحص")
    IssuesList(state.check, state.currentText.isNotBlank(), actions.onFix)
    Hint("اختر أداة من الشريط السفلي: الصنف، القناة، النص، التصميم، الصورة، الموعد.")
}

@Composable
private fun IssuesList(check: CheckResult, hasText: Boolean, onFix: (Issue) -> Unit) {
    if (!hasText) {
        Hint("اكتب نصًا أو اختر صيغة ليُفحص.")
        return
    }
    if (check.issues.isEmpty()) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.cady.success)
            Spacer(Modifier.width(8.dp))
            Text("اجتاز فحص الادعاءات والأرقام", color = MaterialTheme.cady.success)
        }
        return
    }
    Column(
        modifier = Modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        check.errors.distinctBy { it.message }.forEach { IssueRow(it, true, onFix) }
        check.warnings.distinctBy { it.message }.forEach { IssueRow(it, false, onFix) }
    }
}

@Composable
private fun IssueRow(issue: Issue, isError: Boolean, onFix: (Issue) -> Unit) {
    val color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.cady.warning
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (isError) Icons.Filled.Warning else Icons.Filled.Info,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = issue.message,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            modifier = Modifier.weight(1f),
        )
        if (issue.match != null && issue.kind != IssueKind.UNKNOWN_PLACEHOLDER) {
            TextButton(onClick = { onFix(issue) }) { Text("احذفها") }
        }
    }
}

// ---------------------------------------------------------------------- product

@Composable
private fun ProductPanel(state: StudioState, actions: StudioActions) {
    SectionLabel("الصنف")
    Column(
        modifier = Modifier.padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.products.chunked(3).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { product ->
                    ProductCard(
                        product = product,
                        selected = product.id == state.productId,
                        loader = actions.loadThumb,
                        onClick = { actions.onProduct(product.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
    Hint("الأصناف والأسعار والعروض تُقرأ من كادي. اربط صورة بكل صنف من «الأصناف والعروض» في «المزيد».")
}

@Composable
private fun ProductCard(
    product: Product,
    selected: Boolean,
    loader: suspend (String) -> android.graphics.Bitmap?,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    CadyCard(onClick = onClick, modifier = modifier, selected = selected) {
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
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp)),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = product.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            Text(
                text = ArabicText.formatNumber(product.effectivePrice),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.primary,
            )
            if (product.hasOffer) {
                Text("عرض", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.cady.warning)
            }
        }
    }
}

// ---------------------------------------------------------------------- channel

@Composable
private fun ChannelPanel(state: StudioState, actions: StudioActions) {
    SectionLabel("القناة")
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Channel.entries.forEach { channel ->
            FilterChip(
                selected = channel == state.channel,
                onClick = { actions.onChannel(channel) },
                label = { Text(channel.label) },
            )
        }
    }
    Hint("مقاس الصورة: ${state.channel.sizeLabel}. يتغير النص والرابط مع القناة.")
}

// ---------------------------------------------------------------------- text

@Composable
private fun TextPanel(state: StudioState, actions: StudioActions) {
    SectionLabel("النبرة")
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.tones.forEach { tone ->
            FilterChip(selected = tone == state.tone, onClick = { actions.onTone(tone) }, label = { Text(tone) })
        }
    }
    SectionLabel("الزاوية")
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        state.angles.forEach { angle ->
            FilterChip(selected = angle == state.angle, onClick = { actions.onAngle(angle) }, label = { Text(angle) })
        }
    }

    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = actions.onGenerate) { Text("ولّد صيغًا") }
        OutlinedButton(onClick = actions.onIdeas, enabled = !state.aiBusy) { Text("💡 أفكار") }
        OutlinedButton(onClick = actions.onAi, enabled = !state.aiBusy) {
            if (state.aiBusy) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Text("✨ ذكاء")
            }
        }
        OutlinedButton(onClick = actions.onCustomerName) { Text("+ اسم العميل") }
        OutlinedButton(onClick = actions.onCopy) { Text("نسخ") }
    }
    TextButton(
        onClick = actions.onGatewaySetup,
        modifier = Modifier.padding(horizontal = 4.dp),
    ) {
        Text(if (state.gateway.isConfigured) "إعدادات بوابة الذكاء" else "ربط الذكاء: الصق رابط البوابة")
    }

    if (state.ideas.isNotEmpty()) {
        SectionLabel("أفكار")
        Column(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.ideas.forEachIndexed { index, idea ->
                CadyCard {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(idea, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { actions.onUseIdea(index) }) { Text("اكتب منشورًا") }
                    }
                }
            }
            TextButton(onClick = actions.onIdeas) { Text("فكرة أخرى") }
        }
    }

    SectionLabel("النص")
    OutlinedTextField(
        value = state.currentText,
        onValueChange = actions.onText,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        textStyle = LocalTextStyle.current,
        minLines = 4,
        maxLines = 10,
    )
    Spacer(Modifier.height(6.dp))
    IssuesList(state.check, state.currentText.isNotBlank(), actions.onFix)
}

// ---------------------------------------------------------------------- design

@Composable
private fun DesignPanel(state: StudioState, actions: StudioActions) {
    SectionLabel("التصميم")
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PosterDesigns.all.forEach { design ->
            val selected = design.id == state.designId
            val scheme = MaterialTheme.colorScheme
            Column(
                modifier = Modifier
                    .width(104.dp)
                    .clickable { actions.onDesign(design.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(state.channel.aspect)
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = if (selected) 2.dp else 1.dp,
                            color = if (selected) scheme.primary else scheme.outlineVariant,
                            shape = RoundedCornerShape(8.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val thumb = state.thumbs[design.id]
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
                }
                Text(
                    text = design.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
    Hint("تتغير المعاينة فور الاختيار.")
    Hint("تعرض الصورة المختارة: " + PosterDesigns.all.filter { it.usesPicture }.joinToString("، ") { it.label })
}

// ---------------------------------------------------------------------- picture

@Composable
private fun PicturePanel(state: StudioState, actions: StudioActions) {
    SectionLabel("الصورة")
    Row(
        modifier = Modifier.padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(onClick = actions.onPickPicture) { Text("🖼️ اختيار من المعرض") }
        if (state.pictureFile != null) {
            OutlinedButton(onClick = actions.onRemovePicture) { Text("إزالة الصورة") }
        }
    }
    if (state.pictureFile == null && state.product?.picturePath != null) {
        Hint("الصورة الحالية هي صورة الصنف المرتبطة به. اختر صورة هنا لتخص هذا المنشور وحده.")
    }
    Hint("تُنسخ الصورة داخل التطبيق، فيبقى المنشور قابلًا لإعادة الرسم حتى لو حُذفت من المعرض.")
    Hint("تعرض الصورة المختارة: " + PosterDesigns.all.filter { it.usesPicture }.joinToString("، ") { it.label })
}

// ---------------------------------------------------------------------- schedule

@Composable
private fun SchedulePanel(state: StudioState, actions: StudioActions) {
    SectionLabel("الحملة")
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
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
        OutlinedButton(onClick = actions.onNewCampaign) { Text("+ حملة جديدة") }
    }
    val linked = state.campaign
    Hint(
        if (linked == null) {
            "اختر حملة لتحمل روابط المنشور رمزها، فيُنسب كل طلب إلى الحملة."
        } else {
            "رمز الحملة ${linked.code}: روابط النص تحمله، فيُنسب كل طلب يأتي منها إلى «${linked.name}»."
        },
    )

    SectionLabel("موعد النشر")
    val schedule = state.scheduledAt
    Text(
        text = if (schedule == null) "لم يُحدَّد موعد" else schedule.replace("T", " · "),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.padding(horizontal = 12.dp),
    )
    Row(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = actions.onPickDate) { Text("التاريخ") }
        OutlinedButton(onClick = actions.onPickTime) { Text("الوقت") }
        if (schedule != null) {
            TextButton(onClick = actions.onClearSchedule) { Text("مسح الموعد") }
        }
    }
}
