package com.cady.cadymarketing.feature.reports

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.EmptyState
import com.cady.cadymarketing.core.components.PhasePill
import com.cady.cadymarketing.core.components.SectionTitle
import com.cady.cadymarketing.core.theme.cady
import com.cady.cadymarketing.domain.engine.ArabicText
import com.cady.cadymarketing.domain.engine.DateUtil
import com.cady.cadymarketing.domain.engine.RelativeTime
import com.cady.cadymarketing.domain.model.Campaign
import com.cady.cadymarketing.domain.model.CampaignReport
import com.cady.cadymarketing.domain.model.ManualResult
import com.cady.cadymarketing.domain.model.ReportSummary

/**
 * The reports: for each campaign, the orders and sales the systems counted through its code, the numbers written by
 * hand, and what each channel brought. Reading the orders is on demand (the refresh button) and cached on the device.
 */
@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    viewModel: ReportsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var addFor by remember { mutableStateOf<Campaign?>(null) }
    var showSources by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { text ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    CadyScreen(
        title = "التقارير",
        onBack = onBack,
        withBottomInset = true,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (state.report != null) {
                IconButton(onClick = { showReport = true }) { Icon(Icons.Filled.Info, contentDescription = "تشخيص قراءة الطلبات") }
            }
            IconButton(onClick = { showSources = true }) { Icon(Icons.Filled.Settings, contentDescription = "مصادر الطلبات") }
            IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                Icon(Icons.Filled.Refresh, contentDescription = "قراءة الطلبات")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ReportPeriod.entries.forEach { option ->
                    FilterChip(
                        selected = state.period == option,
                        onClick = { viewModel.setPeriod(option) },
                        label = { Text(option.label) },
                    )
                }
            }
            val summary = state.summary
            when {
                !state.loaded || summary == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                summary.campaigns.isEmpty() -> EmptyState(
                    icon = Icons.Filled.BarChart,
                    title = "لا حملات بعد",
                    message = "التقرير يُبنى على الحملات: أنشئ حملة وانشر برابطها، فتُنسب إليها الطلبات برمزها.",
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item { SummaryCard(summary) }
                    item { SourcesNotice(state) }
                    item { SectionTitle("الحملات") }
                    items(summary.campaigns, key = { it.campaign.id }) { report ->
                        CampaignReportCard(
                            report = report,
                            results = state.results.filter { it.campaignId == report.campaign.id },
                            expanded = state.expandedId == report.campaign.id,
                            onToggle = { viewModel.toggle(report.campaign.id) },
                            onAdd = { addFor = report.campaign },
                            onDeleteResult = viewModel::deleteResult,
                        )
                    }
                }
            }
        }
    }

    addFor?.let { campaign ->
        AddResultDialog(
            campaign = campaign,
            onSave = { channel, metric, value, date, note ->
                viewModel.addResult(campaign.id, channel, metric, value, date, note)
                addFor = null
            },
            onDismiss = { addFor = null },
        )
    }
    if (showSources) {
        SourcesDialog(
            sources = state.sources,
            onSave = {
                viewModel.saveSources(it)
                showSources = false
            },
            onDismiss = { showSources = false },
        )
    }
    val report = state.report
    if (showReport && report != null) OrdersReportDialog(report = report, onDismiss = { showReport = false })
}

@Composable
private fun SummaryCard(summary: ReportSummary) {
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Kpi("الطلبات", ArabicText.formatNumber(summary.orders), Modifier.weight(1f))
                Kpi("المبيعات", ArabicText.formatNumber(summary.revenue) + " ريال", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Kpi("منشورات نُشرت", summary.publishedPosts.toString(), Modifier.weight(1f))
                Kpi("أفضل قناة", summary.bestChannel?.label ?: "—", Modifier.weight(1f))
            }
            if (summary.cancelled > 0) {
                Text(
                    text = "${summary.cancelled} طلبًا ملغى لا يدخل في الأرقام.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (summary.orphanOrders > 0) {
                Text(
                    text = "${summary.orphanOrders} طلبًا برمز لا يطابق أي حملة هنا (" +
                        summary.orphanCodes.take(MAX_ORPHAN_CODES).joinToString("، ") + "): رمز خاطئ أو حملة محذوفة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.cady.warning,
                )
            }
        }
    }
}

@Composable
private fun Kpi(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Says when the orders were last read, or what to do when they never were or the last read failed. */
@Composable
private fun SourcesNotice(state: ReportsState) {
    val failures = state.report?.failures.orEmpty()
    val last = state.lastRefresh
    val text = when {
        state.isPreview -> "وضع المعاينة: الطلبات تُقرأ بعد ربط Firebase. يمكنك الآن تسجيل نتائج يدوية."
        failures.isNotEmpty() -> failures.joinToString("\n")
        last == null -> "لم تُقرأ الطلبات بعد: اضغط ⟳ في الأعلى. إن لم تظهر طلبات فافتح «مصادر الطلبات» وطابق أسماء الحقول."
        else -> "آخر قراءة للطلبات " + RelativeTime.ago(System.currentTimeMillis() - last) +
            ". تُحسب فقط الطلبات التي تحمل رمز حملة."
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = if (failures.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun CampaignReportCard(
    report: CampaignReport,
    results: List<ManualResult>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onAdd: () -> Unit,
    onDeleteResult: (String) -> Unit,
) {
    val campaign = report.campaign
    CadyCard(modifier = Modifier.fillMaxWidth(), onClick = onToggle) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = campaign.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                PhasePill(campaign.phase(DateUtil.today()))
            }
            if (report.hasAnyResult) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Kpi("طلبات", ArabicText.formatNumber(report.orders), Modifier.weight(1f))
                    Kpi("مبيعات", ArabicText.formatNumber(report.revenue) + " ريال", Modifier.weight(1f))
                }
            } else {
                Text(
                    text = "لا نتائج بعد لهذه الحملة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                text = "${report.posts} منشورًا، منها ${report.published} نُشر · رمز الحملة ${campaign.code}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (expanded) CampaignDetails(report = report, results = results, onAdd = onAdd, onDeleteResult = onDeleteResult)
        }
    }
}

@Composable
private fun CampaignDetails(
    report: CampaignReport,
    results: List<ManualResult>,
    onAdd: () -> Unit,
    onDeleteResult: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "من الموقع ${report.siteOrders} · من فواتير كادي ${report.kadyOrders} · مكتوبة يدويًا ${report.manualOrders}",
            style = MaterialTheme.typography.bodyMedium,
        )
        if (report.reach > 0L || report.replies > 0L) {
            Text(
                text = "وصول ${ArabicText.formatNumber(report.reach)} · ردود ${ArabicText.formatNumber(report.replies)}",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (report.cancelled > 0) {
            Text(
                text = "${report.cancelled} طلبًا ملغى لا يُحسب",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (report.byChannel.isNotEmpty()) {
            SectionTitle("بحسب القناة")
            report.byChannel.forEach { stat ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stat.channel?.label ?: "غير محددة", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(
                        text = "${stat.orders} طلب · ${ArabicText.formatNumber(stat.revenue)} ريال",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
        if (results.isNotEmpty()) {
            SectionTitle("ما كُتب يدويًا")
            results.take(MAX_RESULTS_SHOWN).forEach { result ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = result.date + " · " + result.metric.label + " " + ArabicText.formatNumber(result.value) +
                                (result.channel?.let { " · " + it.label } ?: ""),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (result.note.isNotBlank()) {
                            Text(result.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    IconButton(onClick = { onDeleteResult(result.id) }) {
                        Icon(Icons.Filled.Delete, contentDescription = "حذف النتيجة")
                    }
                }
            }
        }
        OutlinedButton(onClick = onAdd) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("نتيجة يدوية")
        }
    }
}

private const val MAX_ORPHAN_CODES = 5
private const val MAX_RESULTS_SHOWN = 10
