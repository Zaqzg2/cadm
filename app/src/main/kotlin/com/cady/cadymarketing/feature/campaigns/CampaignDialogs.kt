package com.cady.cadymarketing.feature.campaigns

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import com.cady.cadymarketing.domain.engine.CampaignRules
import com.cady.cadymarketing.domain.model.Campaign
import com.cady.cadymarketing.feature.studio.DatePickerDialogHost

/** A QR card ready to show: what it is for, the link it holds, and the picture. */
data class QrPreview(val title: String, val link: String, val bitmap: Bitmap)

private enum class DateTarget { START, END }

/**
 * The campaign form: name, goal and the two optional dates. [initial] is null for a new campaign.
 * Nothing is saved until the form passes the same checks the engine applies.
 */
@Composable
fun CampaignFormDialog(
    initial: Campaign?,
    onSave: (name: String, goal: String, startDate: String?, endDate: String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var goal by remember { mutableStateOf(initial?.goal.orEmpty()) }
    var start by remember { mutableStateOf(initial?.startDate) }
    var end by remember { mutableStateOf(initial?.endDate) }
    var picking by remember { mutableStateOf<DateTarget?>(null) }
    var attempted by remember { mutableStateOf(false) }
    val problems = CampaignRules.validate(name, start, end)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "حملة جديدة" else "تعديل الحملة") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الحملة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = goal,
                    onValueChange = { goal = it },
                    label = { Text("الهدف (اختياري)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                DateRow("تاريخ البداية", start, onPick = { picking = DateTarget.START }, onClear = { start = null })
                DateRow("تاريخ النهاية", end, onPick = { picking = DateTarget.END }, onClear = { end = null })
                if (attempted) {
                    problems.forEach { problem ->
                        Text(problem, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                if (initial != null) {
                    Text(
                        text = "رمز الحملة ${initial.code} لا يتغير، فتبقى الروابط المنشورة صالحة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    attempted = true
                    if (problems.isEmpty()) onSave(name.trim(), goal.trim(), start, end)
                },
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )

    val target = picking
    if (target != null) {
        DatePickerDialogHost(
            initialDate = if (target == DateTarget.START) start else end,
            onConfirm = { date ->
                if (target == DateTarget.START) start = date else end = date
                picking = null
            },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun DateRow(label: String, value: String?, onPick: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value ?: "غير محدد", style = MaterialTheme.typography.bodyLarge)
        }
        OutlinedButton(onClick = onPick) { Text(if (value == null) "اختيار" else "تغيير") }
        if (value != null) TextButton(onClick = onClear) { Text("مسح") }
    }
}

/** The QR of a campaign link, with what to do with it: copy the link, save the picture, share it. */
@Composable
fun QrDialog(
    preview: QrPreview,
    onCopy: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(preview.title) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                val image = remember(preview.bitmap) { preview.bitmap.asImageBitmap() }
                Image(
                    bitmap = image,
                    contentDescription = "رمز QR",
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(preview.bitmap.width.toFloat() / preview.bitmap.height.toFloat()),
                )
                Text(
                    text = preview.link,
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = onCopy) { Text("نسخ الرابط") }
                    TextButton(onClick = onSave) { Text("حفظ الصورة") }
                    TextButton(onClick = onShare) { Text("مشاركة") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}
