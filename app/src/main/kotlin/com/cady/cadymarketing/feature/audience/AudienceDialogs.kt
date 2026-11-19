package com.cady.cadymarketing.feature.audience

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.cady.cadymarketing.data.audience.CustomerRefreshReport
import com.cady.cadymarketing.domain.engine.AudienceEngine
import com.cady.cadymarketing.domain.model.ConsentStatus
import com.cady.cadymarketing.domain.model.Customer
import com.cady.cadymarketing.domain.model.Segment
import com.cady.cadymarketing.domain.model.SegmentKind

/** What the customer agreed to, with a note on how (the manager's own words). Nothing is ever recorded without this step. */
@Composable
fun ConsentDialog(
    row: CustomerRow,
    onSave: (ConsentStatus, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var status by remember { mutableStateOf(row.status) }
    var note by remember { mutableStateOf(row.consent?.note.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(row.customer.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "سجّل موافقة العميل فقط بعد أن يطلب هو الرسائل أو يوافق عليها صراحة. من طلب التوقف لا يُرسَل إليه أبدًا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                ConsentStatus.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { status = option },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = status == option, onClick = { status = option })
                        Text(option.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظة (كيف ومتى؟)") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(status, note) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** The segment form. [initial] is null for a new segment. A manual segment opens a picker for its customers. */
@Composable
fun SegmentDialog(
    initial: Segment?,
    customers: List<Customer>,
    reps: List<String>,
    onSave: (name: String, kind: SegmentKind, area: String, repName: String, memberIds: List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var kind by remember { mutableStateOf(initial?.kind ?: SegmentKind.FILTER) }
    var area by remember { mutableStateOf(initial?.area.orEmpty()) }
    var rep by remember { mutableStateOf(initial?.repName.orEmpty()) }
    var members by remember { mutableStateOf(initial?.memberIds.orEmpty()) }
    var picking by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    val problems = AudienceEngine.validate(name, kind, members)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "شريحة جديدة" else "تعديل الشريحة") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الشريحة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SegmentKind.entries.forEach { option ->
                        FilterChip(selected = kind == option, onClick = { kind = option }, label = { Text(option.label) })
                    }
                }
                if (kind == SegmentKind.FILTER) {
                    OutlinedTextField(
                        value = area,
                        onValueChange = { area = it },
                        label = { Text("المنطقة تحتوي على (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = rep,
                        onValueChange = { rep = it },
                        label = { Text("المندوب (اختياري)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (reps.isNotEmpty()) {
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            reps.forEach { option ->
                                AssistChip(onClick = { rep = option }, label = { Text(option) })
                            }
                        }
                    }
                    Text(
                        text = "اترك الحقلين فارغين لتشمل الشريحة كل العملاء. لا يُرسَل إلا لمن وافق منهم.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("اختيار العملاء (${members.size})")
                    }
                }
                if (attempted) {
                    problems.forEach { problem ->
                        Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    attempted = true
                    if (problems.isEmpty()) onSave(name.trim(), kind, area, rep, members)
                },
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )

    if (picking) {
        CustomerPickerDialog(
            customers = customers,
            selected = members,
            onDone = { ids ->
                members = ids
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

/** Picks customers by hand, with a search box. The list keeps what was picked, even for customers hidden by the search. */
@Composable
fun CustomerPickerDialog(
    customers: List<Customer>,
    selected: List<String>,
    onDone: (List<String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf(selected.toSet()) }
    var query by remember { mutableStateOf("") }
    val needle = query.trim()
    val shown = if (needle.isEmpty()) customers else customers.filter {
        it.name.contains(needle, ignoreCase = true) || it.area.contains(needle, ignoreCase = true)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختيار العملاء (${picked.size})") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("بحث بالاسم أو المنطقة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                    items(shown, key = { it.id }) { customer ->
                        val checked = customer.id in picked
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { picked = if (checked) picked - customer.id else picked + customer.id },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { picked = if (it) picked + customer.id else picked - customer.id },
                            )
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                Text(customer.name, style = MaterialTheme.typography.bodyLarge)
                                if (customer.area.isNotBlank()) {
                                    Text(
                                        customer.area,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDone(customers.map { it.id }.filter { it in picked }) }) { Text("تم") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** What the last read of Kady's customers brought, with the first document as it arrived (to spot a field-name mismatch). */
@Composable
fun CustomerReportDialog(report: CustomerRefreshReport, onDismiss: () -> Unit) {
    val ltr = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تشخيص قراءة العملاء") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("فُهم ${report.customers} عميلًا، منهم ${report.withoutPhone} بلا رقم واتساب صالح.")
                if (report.skipped.isNotEmpty()) {
                    Text("تُخطّي ${report.skipped.size}:", style = MaterialTheme.typography.labelLarge)
                    report.skipped.take(MAX_SKIPPED).forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Text("أول مستند كما وصل:", style = MaterialTheme.typography.labelLarge)
                Text(report.sample, style = ltr.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                Text(
                    text = "إن كان رقم العميل أو اسمه تحت اسم حقل مختلف فأرسل لي هذا النص، فإضافته سطر واحد في CustomerMapper.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

private const val MAX_SKIPPED = 5
