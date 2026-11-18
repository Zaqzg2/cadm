package com.cady.cadymarketing.feature.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.ConfirmDialog
import com.cady.cadymarketing.core.components.PhasePill
import com.cady.cadymarketing.core.components.Pill
import com.cady.cadymarketing.core.components.StatusPill
import com.cady.cadymarketing.core.theme.cady
import com.cady.cadymarketing.domain.engine.CalendarCell
import com.cady.cadymarketing.domain.engine.CalendarMath
import com.cady.cadymarketing.domain.engine.DayEntries
import com.cady.cadymarketing.domain.model.Campaign
import com.cady.cadymarketing.domain.model.OccasionDay
import com.cady.cadymarketing.domain.model.SavedContent

/**
 * A month grid (week from Saturday) with a dot for each kind of thing on a day, and below it the details of the picked
 * day: its occasions, the campaigns running, and the posts scheduled.
 */
@Composable
fun CalendarScreen(
    onNewPost: (String) -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenCampaign: (String) -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showOccasion by remember { mutableStateOf(false) }
    var deleteOccasionId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { text ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    CadyScreen(
        title = "التقويم",
        snackbarHost = { SnackbarHost(snackbar) },
        actions = { TextButton(onClick = viewModel::goToday) { Text("اليوم") } },
    ) { padding ->
        val weeks = state.grid.chunked(DAYS_IN_WEEK)
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            item {
                MonthHeader(
                    title = CalendarMath.monthTitle(state.year, state.month),
                    onPrevious = { viewModel.showMonth(-1) },
                    onNext = { viewModel.showMonth(1) },
                )
            }
            item { WeekdayHeader() }
            items(weeks) { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { cell ->
                        DayCell(
                            cell = cell,
                            entries = state.days[cell.date],
                            isToday = cell.date == state.today,
                            isSelected = cell.date == state.selected,
                            onClick = { viewModel.select(cell.date) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item { Legend() }
            item {
                DayDetails(
                    date = state.selected,
                    entries = state.selectedEntries,
                    onNewPost = { onNewPost(state.selected) },
                    onNewOccasion = { showOccasion = true },
                    onDeleteOccasion = { id -> deleteOccasionId = id },
                    onOpenPost = onOpenPost,
                    onOpenCampaign = onOpenCampaign,
                )
            }
        }
    }

    if (showOccasion) {
        OccasionDialog(
            date = state.selected,
            onSave = { title, yearly ->
                viewModel.addOccasion(title, state.selected, yearly)
                showOccasion = false
            },
            onDismiss = { showOccasion = false },
        )
    }
    val pendingDelete = deleteOccasionId
    if (pendingDelete != null) {
        ConfirmDialog(
            title = "حذف المناسبة",
            message = "تُحذف المناسبة من التقويم. المناسبات الجاهزة (الأعياد) لا تُحذف.",
            confirmLabel = "حذف",
            onConfirm = {
                viewModel.deleteOccasion(pendingDelete)
                deleteOccasionId = null
            },
            onDismiss = { deleteOccasionId = null },
        )
    }
}

private const val DAYS_IN_WEEK = 7

@Composable
private fun MonthHeader(title: String, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        // The layout is right to left: the previous month sits on the right, so its arrow points right.
        IconButton(onClick = onPrevious) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "الشهر السابق")
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = onNext) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "الشهر التالي")
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        CalendarMath.weekdayNames.forEach { name ->
            Text(
                text = name,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DayCell(
    cell: CalendarCell,
    entries: DayEntries?,
    isToday: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    val numberColor = when {
        isSelected -> scheme.onPrimaryContainer
        cell.inMonth -> scheme.onSurface
        else -> scheme.onSurfaceVariant.copy(alpha = 0.45f)
    }
    Column(
        modifier = modifier
            .padding(2.dp)
            .height(54.dp)
            .clip(shape)
            .background(if (isSelected) scheme.primaryContainer else Color.Transparent)
            .border(
                width = if (isToday) 1.5.dp else 0.dp,
                color = if (isToday) scheme.primary else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = (CalendarMath.dayOf(cell.date) ?: 0).toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = numberColor,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
        )
        Row(
            modifier = Modifier.height(10.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (entries != null && entries.content.isNotEmpty()) Dot(scheme.primary)
            if (entries != null && entries.campaigns.isNotEmpty()) Dot(MaterialTheme.cady.info)
            if (entries != null && entries.occasions.isNotEmpty()) Dot(scheme.tertiary)
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Composable
private fun Legend() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendItem(MaterialTheme.colorScheme.primary, "منشورات")
        LegendItem(MaterialTheme.cady.info, "حملات")
        LegendItem(MaterialTheme.colorScheme.tertiary, "مناسبات")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Dot(color)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun DayDetails(
    date: String,
    entries: DayEntries?,
    onNewPost: () -> Unit,
    onNewOccasion: () -> Unit,
    onDeleteOccasion: (String) -> Unit,
    onOpenPost: (String) -> Unit,
    onOpenCampaign: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(CalendarMath.longDate(date), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onNewPost) { Text("+ منشور في هذا اليوم") }
            OutlinedButton(onClick = onNewOccasion) { Text("+ مناسبة") }
        }
        if (entries == null || entries.isEmpty) {
            Text(
                text = "لا شيء في هذا اليوم بعد.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        entries.occasions.forEach { occasion -> OccasionRow(occasion, onDeleteOccasion) }
        entries.campaigns.forEach { campaign -> CampaignRow(campaign, date, onClick = { onOpenCampaign(campaign.id) }) }
        entries.content.forEach { post -> PostRow(post, onClick = { onOpenPost(post.id) }) }
    }
}

@Composable
private fun OccasionRow(occasion: OccasionDay, onDelete: (String) -> Unit) {
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.tertiary),
            )
            Spacer(Modifier.width(10.dp))
            Text(occasion.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            val id = occasion.id
            if (occasion.builtIn) {
                Pill(text = "تقريبي", modifier = Modifier.padding(end = 8.dp))
            } else if (id != null) {
                IconButton(onClick = { onDelete(id) }) {
                    Icon(Icons.Filled.Delete, contentDescription = "حذف المناسبة")
                }
            }
        }
    }
}

@Composable
private fun CampaignRow(campaign: Campaign, date: String, onClick: () -> Unit) {
    CadyCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.cady.info),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "حملة: " + campaign.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = campaign.periodText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            PhasePill(campaign.phase(date))
        }
    }
}

@Composable
private fun PostRow(post: SavedContent, onClick: () -> Unit) {
    CadyCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Text(
                    text = post.productName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusPill(post.status)
            }
            Text(
                text = post.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = post.channel.label + " · " + post.scheduledAt?.substringAfter('T', "").orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OccasionDialog(date: String, onSave: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var title by remember { mutableStateOf("") }
    var yearly by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مناسبة جديدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(CalendarMath.longDate(date), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("اسم المناسبة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("تتكرر كل سنة في هذا اليوم", modifier = Modifier.weight(1f))
                    Switch(checked = yearly, onCheckedChange = { yearly = it })
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(title, yearly) }, enabled = title.isNotBlank()) { Text("إضافة") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
