package com.cady.cadymarketing.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.Pill
import com.cady.cadymarketing.core.components.SectionTitle
import com.cady.cadymarketing.core.theme.cady
import com.cady.cadymarketing.domain.engine.AlertLevel
import com.cady.cadymarketing.domain.engine.AlertTarget
import com.cady.cadymarketing.domain.engine.CalendarMath
import com.cady.cadymarketing.domain.engine.DailyIdea
import com.cady.cadymarketing.domain.engine.HomeAlert
import com.cady.cadymarketing.domain.engine.HomeStats

/**
 * The first tab: the idea of the day, the numbers that matter, and what needs attention. Every card leads somewhere:
 * [onStudio] opens the Studio (on a product, or on the idea of the day), the others open the tabs and screens.
 */
@Composable
fun HomeScreen(
    onStudio: (productId: String?, ideaOfDay: Boolean) -> Unit,
    onLibrary: () -> Unit,
    onCampaigns: () -> Unit,
    onProducts: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    CadyScreen(title = "الرئيسية") { padding ->
        if (!state.loaded) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@CadyScreen
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    text = CalendarMath.longDate(state.today),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                val idea = state.idea
                if (idea != null) {
                    IdeaCard(idea = idea, onWrite = { onStudio(idea.product.id, true) })
                } else {
                    NoIdeaCard(hasProducts = state.hasProducts, onProducts = onProducts, onStudio = { onStudio(null, false) })
                }
            }
            item { StatsGrid(stats = state.stats, onLibrary = onLibrary, onCampaigns = onCampaigns) }
            item { SectionTitle("ما يحتاج انتباهك") }
            if (state.alerts.isEmpty()) {
                item { AllClearCard() }
            } else {
                items(state.alerts.size) { index ->
                    val alert = state.alerts[index]
                    AlertCard(
                        alert = alert,
                        onClick = {
                            when (alert.target) {
                                AlertTarget.LIBRARY -> onLibrary()
                                AlertTarget.CAMPAIGNS -> onCampaigns()
                                AlertTarget.PRODUCTS -> onProducts()
                                AlertTarget.STUDIO -> onStudio(alert.productId, false)
                            }
                        },
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                    Button(onClick = { onStudio(null, false) }, modifier = Modifier.weight(1f)) { Text("منشور جديد") }
                    OutlinedButton(onClick = onLibrary, modifier = Modifier.weight(1f)) { Text("المكتبة") }
                }
            }
        }
    }
}

@Composable
private fun IdeaCard(idea: DailyIdea, onWrite: () -> Unit) {
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "فكرة اليوم",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                Pill(text = idea.angle)
            }
            Text(
                text = idea.product.name,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(text = idea.idea, style = MaterialTheme.typography.bodyLarge)
            Button(onClick = onWrite, modifier = Modifier.fillMaxWidth()) { Text("اكتب منشورًا بهذه الفكرة") }
        }
    }
}

@Composable
private fun NoIdeaCard(hasProducts: Boolean, onProducts: () -> Unit, onStudio: () -> Unit) {
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("فكرة اليوم", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text(
                text = if (hasProducts) {
                    "لا توجد فكرة اليوم. افتح الاستوديو واكتب منشورك."
                } else {
                    "لا توجد أصناف بعد. حدّث الأصناف من كادي لتظهر لك أفكار كل يوم."
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (hasProducts) {
                Button(onClick = onStudio, modifier = Modifier.fillMaxWidth()) { Text("افتح الاستوديو") }
            } else {
                OutlinedButton(onClick = onProducts, modifier = Modifier.fillMaxWidth()) { Text("الأصناف") }
            }
        }
    }
}

@Composable
private fun StatsGrid(stats: HomeStats, onLibrary: () -> Unit, onCampaigns: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("حملات جارية", stats.runningCampaigns, onCampaigns, Modifier.weight(1f))
            StatCard("مجدولة هذا الأسبوع", stats.scheduledNextWeek, onLibrary, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("مسودات", stats.drafts, onLibrary, Modifier.weight(1f))
            StatCard("بانتظار الاعتماد", stats.pending, onLibrary, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("معتمدة", stats.approved, onLibrary, Modifier.weight(1f))
            StatCard("منشورة هذا الشهر", stats.publishedThisMonth, onLibrary, Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatCard(label: String, value: Int, onClick: () -> Unit, modifier: Modifier) {
    CadyCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(value.toString(), style = MaterialTheme.typography.headlineSmall)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AlertCard(alert: HomeAlert, onClick: () -> Unit) {
    val (icon, tint) = alertStyle(alert.level)
    CadyCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = tint)
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(alert.title, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = alert.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun alertStyle(level: AlertLevel): Pair<ImageVector, Color> = when (level) {
    AlertLevel.URGENT -> Icons.Filled.Warning to MaterialTheme.colorScheme.error
    AlertLevel.NOTICE -> Icons.Filled.Warning to MaterialTheme.cady.warning
    AlertLevel.INFO -> Icons.Filled.Info to MaterialTheme.cady.info
}

@Composable
private fun AllClearCard() {
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.cady.success)
            Text("لا شيء يحتاج انتباهك الآن.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
