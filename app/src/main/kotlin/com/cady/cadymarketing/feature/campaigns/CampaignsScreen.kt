package com.cady.cadymarketing.feature.campaigns

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.role.canUse
import com.cady.cadymarketing.domain.engine.Feature
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.EmptyState
import com.cady.cadymarketing.core.components.PhasePill
import com.cady.cadymarketing.core.components.Pill

/** The campaigns tab: every campaign with where it stands, and a way to start a new one. */
@Composable
fun CampaignsScreen(
    onOpenCampaign: (String) -> Unit,
    viewModel: CampaignsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var showForm by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { text ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    // Campaigns are the manager's to make; a marketer sees them and writes posts for them.
    val canManage = canUse(Feature.MANAGE_CAMPAIGNS)
    CadyScreen(
        title = "الحملات",
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (canManage) {
                IconButton(onClick = { showForm = true }) {
                    Icon(Icons.Filled.Add, contentDescription = "حملة جديدة")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                CampaignFilter.entries.forEach { option ->
                    FilterChip(
                        selected = option == state.filter,
                        onClick = { viewModel.setFilter(option) },
                        label = { Text(option.label) },
                    )
                }
            }
            when {
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.visible.isEmpty() -> EmptyState(
                    icon = Icons.Filled.Campaign,
                    title = if (state.items.isEmpty()) "لا توجد حملات بعد" else "لا حملات في هذا القسم",
                    message = "الحملة تجمع منشورات فترة واحدة تحت رمز واحد، فتعرف من أين جاء كل طلب.",
                    actionLabel = if (state.items.isEmpty() && canManage) "إنشاء أول حملة" else null,
                    onAction = { showForm = true },
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.visible, key = { it.campaign.id }) { item ->
                        CampaignCard(item = item, onClick = { onOpenCampaign(item.campaign.id) })
                    }
                }
            }
        }
    }

    if (showForm) {
        CampaignFormDialog(
            initial = null,
            onSave = { name, goal, start, end ->
                viewModel.create(name, goal, start, end)
                showForm = false
            },
            onDismiss = { showForm = false },
        )
    }
}

@Composable
private fun CampaignCard(item: CampaignItem, onClick: () -> Unit) {
    val campaign = item.campaign
    CadyCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = campaign.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PhasePill(item.phase)
            }
            Text(
                text = campaign.periodText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (campaign.goal.isNotBlank()) {
                Text(
                    text = campaign.goal,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(text = "${item.posts} منشور")
                Pill(text = "${item.ready} جاهز")
                Pill(text = "الرمز ${campaign.code}")
            }
        }
    }
}
