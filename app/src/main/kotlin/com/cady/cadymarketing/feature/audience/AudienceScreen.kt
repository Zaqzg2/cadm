package com.cady.cadymarketing.feature.audience

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.ConfirmDialog
import com.cady.cadymarketing.core.components.EmptyState
import com.cady.cadymarketing.core.components.Pill
import com.cady.cadymarketing.core.theme.cady
import com.cady.cadymarketing.domain.engine.RelativeTime
import com.cady.cadymarketing.domain.model.ConsentStatus
import com.cady.cadymarketing.domain.model.Segment
import com.cady.cadymarketing.domain.model.SegmentKind

/**
 * The audience: Kady's customers with what each agreed to, and the segments a post is sent to.
 * [onSend] opens the send list of a segment (with the post, when the screen was opened from one).
 */
@Composable
fun AudienceScreen(
    onBack: () -> Unit,
    onSend: (segmentId: String, contentId: String?) -> Unit,
    viewModel: AudienceViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var consentFor by remember { mutableStateOf<CustomerRow?>(null) }
    var showSegmentForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Segment?>(null) }
    var deleting by remember { mutableStateOf<Segment?>(null) }
    var showReport by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { text ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    CadyScreen(
        title = "الجمهور",
        onBack = onBack,
        withBottomInset = true,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (state.tab == AudienceTab.SEGMENTS) {
                IconButton(
                    onClick = {
                        editing = null
                        showSegmentForm = true
                    },
                ) { Icon(Icons.Filled.Add, contentDescription = "شريحة جديدة") }
            }
            if (state.report != null) {
                IconButton(onClick = { showReport = true }) { Icon(Icons.Filled.Info, contentDescription = "تشخيص القراءة") }
            }
            IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                Icon(Icons.Filled.Refresh, contentDescription = "قراءة العملاء من كادي")
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
                AudienceTab.entries.forEach { tab ->
                    val count = if (tab == AudienceTab.CUSTOMERS) state.customers.size else state.segments.size
                    FilterChip(
                        selected = state.tab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        label = { Text(tab.label + " (" + count + ")") },
                    )
                }
            }
            when {
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                state.tab == AudienceTab.CUSTOMERS -> CustomersTab(
                    state = state,
                    onFilter = viewModel::setFilter,
                    onQuery = viewModel::setQuery,
                    onOpen = { consentFor = it },
                    onRefresh = viewModel::refresh,
                )
                else -> SegmentsTab(
                    state = state,
                    onNew = {
                        editing = null
                        showSegmentForm = true
                    },
                    onEdit = {
                        editing = it
                        showSegmentForm = true
                    },
                    onDelete = { deleting = it },
                    onSend = { segmentId -> onSend(segmentId, viewModel.contentId) },
                )
            }
        }
    }

    consentFor?.let { row ->
        ConsentDialog(
            row = row,
            onSave = { status, note ->
                viewModel.setConsent(row.customer.id, status, note)
                consentFor = null
            },
            onDismiss = { consentFor = null },
        )
    }
    if (showSegmentForm) {
        SegmentDialog(
            initial = editing,
            customers = state.customers,
            reps = state.reps,
            onSave = { name, kind, area, rep, ids ->
                viewModel.saveSegment(editing?.id, name, kind, area, rep, ids)
                showSegmentForm = false
            },
            onDismiss = { showSegmentForm = false },
        )
    }
    deleting?.let { segment ->
        ConfirmDialog(
            title = "حذف الشريحة",
            message = "تُحذف الشريحة «${segment.name}» فقط، ولا يُمَسّ العملاء ولا موافقاتهم.",
            confirmLabel = "حذف",
            onConfirm = {
                viewModel.deleteSegment(segment.id)
                deleting = null
            },
            onDismiss = { deleting = null },
        )
    }
    val report = state.report
    if (showReport && report != null) CustomerReportDialog(report = report, onDismiss = { showReport = false })
}

@Composable
private fun CustomersTab(
    state: AudienceState,
    onFilter: (ConsentFilter) -> Unit,
    onQuery: (String) -> Unit,
    onOpen: (CustomerRow) -> Unit,
    onRefresh: () -> Unit,
) {
    if (state.customers.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Groups,
            title = "لا عملاء بعد",
            message = if (state.isPreview) {
                "وضع المعاينة: يُقرأ العملاء من كادي بعد ربط Firebase."
            } else {
                "اقرأ عملاء كادي لتسجّل موافقة من يريد منهم الرسائل، ثم تبني منهم شرائح الإرسال."
            },
            actionLabel = if (state.isPreview) null else "قراءة العملاء من كادي",
            onAction = onRefresh,
        )
        return
    }
    val last = state.lastRefresh
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "${state.customers.size} عميل · ${state.grantedCount} موافق · ${state.declinedCount} طلب التوقف" +
                (if (last != null) " · آخر قراءة " + RelativeTime.ago(System.currentTimeMillis() - last) else ""),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ConsentFilter.entries.forEach { option ->
                FilterChip(selected = state.filter == option, onClick = { onFilter(option) }, label = { Text(option.label) })
            }
        }
        OutlinedTextField(
            value = state.query,
            onValueChange = onQuery,
            label = { Text("بحث بالاسم أو المنطقة أو الرقم") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        val visible = state.visible
        if (visible.isEmpty()) {
            Text(
                text = "لا عميل يطابق هذا الفلتر.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(visible, key = { it.customer.id }) { row -> CustomerCard(row = row, onClick = { onOpen(row) }) }
            }
        }
    }
}

@Composable
private fun CustomerCard(row: CustomerRow, onClick: () -> Unit) {
    val customer = row.customer
    CadyCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val details = listOf(customer.area, customer.repName).filter { it.isNotBlank() }.joinToString(" · ")
                if (details.isNotEmpty()) {
                    Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val phone = customer.phone
                if (phone != null) {
                    Text(
                        text = "+$phone",
                        style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = if (customer.rawPhone.isBlank()) "بلا رقم" else "رقم غير صالح: " + customer.rawPhone,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
            ConsentPill(row.status)
        }
    }
}

@Composable
private fun ConsentPill(status: ConsentStatus) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.cady
    when (status) {
        ConsentStatus.GRANTED -> Pill("موافق", background = extra.success.copy(alpha = 0.16f), foreground = extra.success)
        ConsentStatus.DECLINED -> Pill("طلب التوقف", background = scheme.errorContainer, foreground = scheme.onErrorContainer)
        ConsentStatus.UNKNOWN -> Pill("بلا موافقة")
    }
}

@Composable
private fun SegmentsTab(
    state: AudienceState,
    onNew: () -> Unit,
    onEdit: (Segment) -> Unit,
    onDelete: (Segment) -> Unit,
    onSend: (String) -> Unit,
) {
    val rows = state.segmentRows
    if (rows.isEmpty()) {
        EmptyState(
            icon = Icons.Filled.Groups,
            title = "لا شرائح بعد",
            message = "الشريحة مجموعة عملاء ترسل لهم المنشور نفسه، كلًّا باسمه. لا يُرسَل إلا لمن سجّلت موافقته.",
            actionLabel = "شريحة جديدة",
            onAction = onNew,
        )
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val post = state.sendingPost
        if (post != null) {
            item {
                CadyCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "اختر الشريحة التي سترسل لها منشور «$post».",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
        }
        items(rows, key = { it.segment.id }) { row ->
            SegmentCard(row = row, onSend = { onSend(row.segment.id) }, onEdit = { onEdit(row.segment) }, onDelete = { onDelete(row.segment) })
        }
    }
}

@Composable
private fun SegmentCard(row: SegmentRow, onSend: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val segment = row.segment
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = segment.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Pill(segment.kind.label)
            }
            Text(
                text = describe(segment),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill("${row.members} عميل")
                Pill("${row.sendable} يمكن الإرسال لهم")
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = onSend, enabled = row.sendable > 0) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("إرسال")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onEdit) { Text("تعديل") }
                TextButton(onClick = onDelete) { Text("حذف") }
            }
        }
    }
}

private fun describe(segment: Segment): String = when (segment.kind) {
    SegmentKind.MANUAL -> "${segment.memberIds.size} عميل مختار بيدك"
    SegmentKind.FILTER -> {
        val parts = ArrayList<String>()
        if (segment.area.isNotBlank()) parts.add("المنطقة: " + segment.area)
        if (segment.repName.isNotBlank()) parts.add("المندوب: " + segment.repName)
        if (parts.isEmpty()) "كل العملاء" else parts.joinToString(" · ")
    }
}
