package com.cady.cadymarketing.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.Appear
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.IconBadge
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
 * The first tab: today's date and what needs attention, the idea of the day, four quick actions, the numbers that
 * matter, and the alerts. Every card leads somewhere: [onStudio] opens the Studio (on a product, or on the idea of
 * the day), the others open the tabs and screens. The cards arrive one after another when the tab first opens.
 */
@Composable
fun HomeScreen(
    onStudio: (productId: String?, ideaOfDay: Boolean) -> Unit,
    onLibrary: () -> Unit,
    onCampaigns: () -> Unit,
    onProducts: () -> Unit,
    onCalendar: () -> Unit = {},
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Appear(index = 0, modifier = Modifier.fillMaxWidth()) {
                    Greeting(today = state.today, alerts = state.alerts.size)
                }
            }
            item {
                Appear(index = 1, modifier = Modifier.fillMaxWidth()) {
                    val idea = state.idea
                    if (idea != null) {
                        IdeaHero(idea = idea, onWrite = { onStudio(idea.product.id, true) })
                    } else {
                        NoIdeaCard(hasProducts = state.hasProducts, onProducts = onProducts, onStudio = { onStudio(null, false) })
                    }
                }
            }
            item {
                Appear(index = 2, modifier = Modifier.fillMaxWidth()) {
                    QuickActions(
                        onNewPost = { onStudio(null, false) },
                        onCalendar = onCalendar,
                        onLibrary = onLibrary,
                        onCampaigns = onCampaigns,
                    )
                }
            }
            item {
                Appear(index = 3, modifier = Modifier.fillMaxWidth()) {
                    StatsGrid(stats = state.stats, onLibrary = onLibrary, onCampaigns = onCampaigns, onCalendar = onCalendar)
                }
            }
            item { Appear(index = 4) { SectionTitle("ما يحتاج انتباهك") } }
            if (state.alerts.isEmpty()) {
                item { Appear(index = 5, modifier = Modifier.fillMaxWidth()) { AllClearCard() } }
            } else {
                items(state.alerts.size) { index ->
                    val alert = state.alerts[index]
                    Appear(index = 5 + index, modifier = Modifier.fillMaxWidth()) {
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
            }
        }
    }
}

/** The date in full, and one line on how the day looks. */
@Composable
private fun Greeting(today: String, alerts: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = CalendarMath.longDate(today),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (alerts == 0) "كل شيء على ما يرام اليوم" else "عندك $alerts من الأمور تحتاج انتباهك",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The idea of the day on the brand gradient: the one panel on the page that is not glass, so it is the first thing seen. */
@Composable
private fun IdeaHero(idea: DailyIdea, onWrite: () -> Unit) {
    val extra = MaterialTheme.cady
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(extra.heroStart, extra.heroEnd)))
            .drawBehind {
                drawCircle(
                    color = Color.White.copy(alpha = 0.10f),
                    radius = size.width * 0.38f,
                    center = Offset(size.width * 0.04f, -size.height * 0.08f),
                )
                drawCircle(
                    color = Color.White.copy(alpha = 0.07f),
                    radius = size.width * 0.30f,
                    center = Offset(size.width * 0.98f, size.height),
                )
            }
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = extra.onHero, modifier = Modifier.size(20.dp))
            }
            Text(
                text = "فكرة اليوم",
                style = MaterialTheme.typography.titleMedium,
                color = extra.onHero,
                modifier = Modifier.weight(1f),
            )
            Pill(text = idea.angle, background = Color.White.copy(alpha = 0.20f), foreground = extra.onHero)
        }
        Text(
            text = idea.product.name,
            style = MaterialTheme.typography.labelLarge,
            color = extra.onHero.copy(alpha = 0.85f),
        )
        Text(text = idea.idea, style = MaterialTheme.typography.bodyLarge, color = extra.onHero)
        Button(
            onClick = onWrite,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = extra.onHero, contentColor = extra.heroStart),
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("اكتب منشورًا بهذه الفكرة")
        }
    }
}

@Composable
private fun NoIdeaCard(hasProducts: Boolean, onProducts: () -> Unit, onStudio: () -> Unit) {
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IconBadge(icon = Icons.Filled.Lightbulb, size = 34.dp, iconSize = 18.dp)
                Text("فكرة اليوم", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            }
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

/** Four doors the person uses every day, as tiles: the same ones the launcher shortcuts and the widget open. */
@Composable
private fun QuickActions(onNewPost: () -> Unit, onCalendar: () -> Unit, onLibrary: () -> Unit, onCampaigns: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickTile("منشور جديد", Icons.Filled.Add, onNewPost, Modifier.weight(1f))
        QuickTile("التقويم", Icons.Filled.CalendarMonth, onCalendar, Modifier.weight(1f))
        QuickTile("المكتبة", Icons.Filled.PhotoLibrary, onLibrary, Modifier.weight(1f))
        QuickTile("الحملات", Icons.Filled.Campaign, onCampaigns, Modifier.weight(1f))
    }
}

@Composable
private fun QuickTile(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier) {
    CadyCard(modifier = modifier, onClick = onClick) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconBadge(icon = icon, size = 38.dp, iconSize = 20.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StatsGrid(stats: HomeStats, onLibrary: () -> Unit, onCampaigns: () -> Unit, onCalendar: () -> Unit) {
    val extra = MaterialTheme.cady
    val scheme = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("حملات جارية", stats.runningCampaigns, Icons.Filled.Campaign, scheme.primary, onCampaigns, Modifier.weight(1f))
            StatCard("مجدولة هذا الأسبوع", stats.scheduledNextWeek, Icons.Filled.CalendarMonth, extra.info, onCalendar, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("مسودات", stats.drafts, Icons.Filled.Edit, scheme.secondary, onLibrary, Modifier.weight(1f))
            StatCard("بانتظار الاعتماد", stats.pending, Icons.Filled.Schedule, extra.warning, onLibrary, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatCard("معتمدة", stats.approved, Icons.Filled.CheckCircle, extra.success, onLibrary, Modifier.weight(1f))
            StatCard("منشورة هذا الشهر", stats.publishedThisMonth, Icons.Filled.Share, extra.info, onLibrary, Modifier.weight(1f))
        }
    }
}

/** One figure: the number large, its name small, and a round tinted icon that says what kind of thing it counts. */
@Composable
private fun StatCard(label: String, value: Int, icon: ImageVector, tint: Color, onClick: () -> Unit, modifier: Modifier) {
    CadyCard(modifier = modifier, onClick = onClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(value.toString(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            }
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
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
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
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.cady.success)
            Text("لا شيء يحتاج انتباهك الآن.", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
