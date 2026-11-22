package com.cady.cadymarketing.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.EmptyState
import com.cady.cadymarketing.core.role.canUse
import com.cady.cadymarketing.domain.engine.Feature
import com.cady.cadymarketing.domain.model.OpCategory
import com.cady.cadymarketing.domain.model.OpLogEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * سجل العمليات: who did what, to which post or campaign, and when, newest first, with a filter by kind.
 * The manager sees every person's lines (they arrive with the sync); a marketer sees what was done on this device.
 */
@Composable
fun LogScreen(
    onBack: () -> Unit,
    viewModel: LogViewModel = hiltViewModel(),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    // Saved as the category's name so it survives a rotation; empty means all.
    var chosen by rememberSaveable { mutableStateOf("") }
    val category = OpCategory.entries.firstOrNull { it.name == chosen }
    val shown = if (category == null) entries else entries.filter { it.category == category }
    val wholeTeam = canUse(Feature.SEE_TEAM_LOG)

    CadyScreen(title = "سجل العمليات", onBack = onBack, withBottomInset = true) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item {
                    FilterChip(selected = category == null, onClick = { chosen = "" }, label = { Text("الكل") })
                }
                items(OpCategory.entries.toList()) { option ->
                    FilterChip(selected = category == option, onClick = { chosen = option.name }, label = { Text(option.label) })
                }
            }
            if (!wholeTeam) {
                Text(
                    text = "تعرض هذه الصفحة ما فعلته على هذا الجهاز.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            if (shown.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.History,
                    title = "لا عمليات بعد",
                    message = "تظهر هنا كل عملية مهمة: إنشاء منشور واعتماده وإرجاعه وحذفه، والمزامنة، والنسخ الاحتياطي.",
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(shown, key = { it.id }) { entry -> LogCard(entry) }
                }
            }
        }
    }
}

@Composable
private fun LogCard(entry: OpLogEntry) {
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(entry.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Text(
                    formatTime(entry.at),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (entry.subject.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(entry.subject, style = MaterialTheme.typography.bodyMedium)
            }
            if (entry.detail.isNotBlank()) {
                Text(entry.detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "بواسطة " + entry.actor.substringBefore('@').ifBlank { entry.actor },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatTime(at: Long): String = SimpleDateFormat("MM-dd HH:mm", Locale.US).format(Date(at))
