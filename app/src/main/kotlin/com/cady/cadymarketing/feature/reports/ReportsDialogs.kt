package com.cady.cadymarketing.feature.reports

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.cady.cadymarketing.data.report.OrdersRefreshReport
import com.cady.cadymarketing.domain.engine.ArabicText
import com.cady.cadymarketing.domain.engine.CampaignRules
import com.cady.cadymarketing.domain.engine.DateUtil
import com.cady.cadymarketing.domain.engine.ReportSources
import com.cady.cadymarketing.domain.model.Campaign
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.ResultMetric
import com.cady.cadymarketing.feature.studio.DatePickerDialogHost

/**
 * A result written by hand for [campaign]: what it is (orders, sales, reach, replies), how many, on which day, and
 * optionally for one channel. Use it for what no system counts for you; what a system already counts is added by itself.
 */
@Composable
fun AddResultDialog(
    campaign: Campaign,
    onSave: (channel: Channel?, metric: ResultMetric, value: Long, date: String, note: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var metric by remember { mutableStateOf(ResultMetric.ORDERS) }
    var channel by remember { mutableStateOf<Channel?>(null) }
    var valueText by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(DateUtil.today()) }
    var note by remember { mutableStateOf("") }
    var pickingDate by remember { mutableStateOf(false) }
    var attempted by remember { mutableStateOf(false) }
    val value = ArabicText.toWesternDigits(valueText).filter { it in '0'..'9' }.toLongOrNull() ?: 0L
    val dateOk = CampaignRules.isDate(date)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("نتيجة لحملة «${campaign.name}»") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ResultMetric.entries.forEach { option ->
                        FilterChip(selected = metric == option, onClick = { metric = option }, label = { Text(option.label) })
                    }
                }
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it },
                    label = { Text("العدد (${metric.unit})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("القناة (اختياري)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = channel == null, onClick = { channel = null }, label = { Text("غير محددة") })
                    Channel.entries.forEach { option ->
                        FilterChip(selected = channel == option, onClick = { channel = option }, label = { Text(option.label) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("اليوم", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(date, style = MaterialTheme.typography.bodyLarge)
                    }
                    OutlinedButton(onClick = { pickingDate = true }) { Text("تغيير") }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("ملاحظة (اختياري)") },
                    minLines = 1,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (attempted && value <= 0L) {
                    Text("اكتب رقمًا أكبر من صفر", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (attempted && !dateOk) {
                    Text("التاريخ غير صالح", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    text = "اكتب هنا ما لا يعدّه نظام لك (مثل طلبات واتساب). الطلبات التي تعدّها الأنظمة تُضاف تلقائيًا، فلا تكتبها مرة ثانية.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    attempted = true
                    if (value > 0L && dateOk) onSave(channel, metric, value, date, note)
                },
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )

    if (pickingDate) {
        DatePickerDialogHost(
            initialDate = date,
            onConfirm = {
                date = it
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

/** The names the reports read orders under: the website's collection and the field that holds the campaign code. */
@Composable
fun SourcesDialog(
    sources: ReportSources,
    onSave: (ReportSources) -> Unit,
    onDismiss: () -> Unit,
) {
    var siteCollection by remember { mutableStateOf(sources.siteCollection) }
    var siteField by remember { mutableStateOf(sources.siteField) }
    var kadyEnabled by remember { mutableStateOf(sources.kadyEnabled) }
    var kadyCollection by remember { mutableStateOf(sources.kadyCollection) }
    var kadyField by remember { mutableStateOf(sources.kadyField) }
    val ltr = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("مصادر الطلبات") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("طلبات الموقع", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = "يجب أن يحفظ الموقع الرمز القادم في ?c= داخل كل طلب، في الحقل المذكور هنا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = siteCollection,
                    onValueChange = { siteCollection = it },
                    label = { Text("اسم مجموعة الطلبات") },
                    textStyle = ltr,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = siteField,
                    onValueChange = { siteField = it },
                    label = { Text("حقل رمز الحملة") },
                    textStyle = ltr,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("فواتير كادي (طلبات واتساب)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Switch(checked = kadyEnabled, onCheckedChange = { kadyEnabled = it })
                }
                Text(
                    text = "فعّلها بعد أن تضيف في كادي حقلًا لرمز الحملة في الفاتورة. بدونه لا فاتورة تحمل رمزًا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (kadyEnabled) {
                    OutlinedTextField(
                        value = kadyCollection,
                        onValueChange = { kadyCollection = it },
                        label = { Text("اسم مجموعة الفواتير") },
                        textStyle = ltr,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = kadyField,
                        onValueChange = { kadyField = it },
                        label = { Text("حقل رمز الحملة في الفاتورة") },
                        textStyle = ltr,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(ReportSources(siteCollection, siteField, kadyEnabled, kadyCollection, kadyField)) }) {
                Text("حفظ")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** What the last read of the orders brought from each source, with a sample document as it arrived. */
@Composable
fun OrdersReportDialog(report: OrdersRefreshReport, onDismiss: () -> Unit) {
    val ltr = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تشخيص قراءة الطلبات") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                report.sources.forEach { source ->
                    Text(source.origin.label, style = MaterialTheme.typography.titleSmall)
                    val total = source.total?.let { " من أصل $it مستندًا في المجموعة" }.orEmpty()
                    Text("المجموعة «${source.collection}»، الحقل «${source.codeField}»: فُهم ${source.read} طلبًا برمز حملة$total.")
                    if (source.read == 0) {
                        Text(
                            text = "لم يُقرأ أي طلب برمز. إن كان في المجموعة طلبات فاسم الحقل غير مطابق: انظر المستند أدناه وصحّح اسم الحقل من «مصادر الطلبات».",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    source.skipped.take(MAX_SKIPPED).forEach { line ->
                        Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("مستند من المجموعة كما وصل:", style = MaterialTheme.typography.labelLarge)
                    Text(source.sample, style = ltr.copy(color = MaterialTheme.colorScheme.onSurfaceVariant))
                }
                report.failures.forEach { failure ->
                    Text(failure, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

private const val MAX_SKIPPED = 3
