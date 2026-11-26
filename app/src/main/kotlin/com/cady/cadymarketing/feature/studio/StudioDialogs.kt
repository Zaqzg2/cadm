package com.cady.cadymarketing.feature.studio

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.ConfirmDialog
import com.cady.cadymarketing.core.components.IconBadge
import com.cady.cadymarketing.core.theme.cady
import com.cady.cadymarketing.domain.engine.DateUtil
import com.cady.cadymarketing.domain.engine.GatewayConfig
import com.cady.cadymarketing.domain.model.Channel
import com.cady.cadymarketing.domain.model.SavedContent
import kotlinx.coroutines.launch

/** The recent saved posts as a sheet. Tapping one opens it in the Studio exactly as it was saved; deleting asks first. */
@Composable
fun SavedContentSheet(
    saved: List<SavedContent>,
    onOpen: (SavedContent) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var pendingDelete by remember { mutableStateOf<SavedContent?>(null) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = "المحتوى المحفوظ",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            if (saved.isEmpty()) {
                Text(
                    text = "لا يوجد محتوى محفوظ بعد. اضغط على أيقونة الحفظ لحفظ مسودة.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 32.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(saved, key = { it.id }) { item ->
                        SavedPostCard(
                            item = item,
                            onClick = { onOpen(item) },
                            trailing = {
                                IconButton(onClick = { pendingDelete = item }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "حذف")
                                }
                            },
                        )
                    }
                }
            }
        }
    }
    pendingDelete?.let { item ->
        ConfirmDialog(
            title = "حذف المحتوى المحفوظ؟",
            message = "سيُحذف منشور «${item.productName}» المحفوظ ولا يمكن استرجاعه.",
            confirmLabel = "حذف",
            onConfirm = {
                onDelete(item.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

/** Where to send the poster and its text (the apps first), then what to keep on the phone. */
@Composable
internal fun ShareSheet(
    onDismiss: () -> Unit,
    onShare: (ShareTarget) -> Unit,
    onSaveImage: () -> Unit,
    onCopy: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    // Slides the sheet away first and then acts, so the choice feels answered.
    fun choose(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            onDismiss()
            action()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = "مشاركة المنشور",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            ShareOption(Icons.Filled.Share, "واتساب", "الصورة والنص معًا") {
                choose { onShare(ShareTarget.WHATSAPP) }
            }
            ShareOption(Icons.Filled.Business, "واتساب للأعمال", "الصورة والنص معًا") {
                choose { onShare(ShareTarget.WHATSAPP_BUSINESS) }
            }
            ShareOption(Icons.Filled.MoreHoriz, "تطبيق آخر", "من قائمة المشاركة في الجهاز") {
                choose { onShare(ShareTarget.OTHER) }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            ShareOption(Icons.Filled.SaveAlt, "حفظ الصورة", "في مجلد التنزيلات على الجهاز") {
                choose { onSaveImage() }
            }
            ShareOption(Icons.Filled.ContentCopy, "نسخ النص", null) {
                choose { onCopy() }
            }
        }
    }
}

@Composable
private fun ShareOption(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    CadyCard(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = icon, size = 40.dp, iconSize = 20.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/**
 * The poster as large as the screen allows. It opens at once with the small preview and swaps in a sharper rendering
 * as soon as it is drawn; a tap anywhere closes it.
 */
@Composable
internal fun PreviewDialog(
    channel: Channel,
    small: Bitmap?,
    loadLarge: suspend () -> Bitmap?,
    onDismiss: () -> Unit,
) {
    val bitmap by produceState<Bitmap?>(initialValue = small) { value = loadLarge() ?: small }
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                )
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = channel.label + " · " + ltr(channel.sizeLabel),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "إغلاق", tint = Color.White)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val shown = bitmap
                if (shown != null) {
                    val image = remember(shown) { shown.asImageBitmap() }
                    Image(
                        bitmap = image,
                        contentDescription = "معاينة الملصق",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .aspectRatio(channel.aspect)
                            .clip(RoundedCornerShape(18.dp)),
                    )
                } else {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }
    }
}

/** The AI gateway link and token, with a connection test that says exactly what is wrong. */
@Composable
fun GatewayDialog(
    config: GatewayConfig,
    testing: Boolean,
    testMessage: String?,
    onTest: (String, String) -> Unit,
    onSave: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var url by remember { mutableStateOf(config.url) }
    var token by remember { mutableStateOf(config.token) }
    val fieldStyle = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("بوابة الذكاء") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "الصق رابط بوابة Apps Script (ينتهي بـ /exec) والرمز إن وُجد. يُحفظان على هذا الجهاز فقط.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("رابط البوابة") },
                    textStyle = fieldStyle,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("الرمز (اختياري)") },
                    textStyle = fieldStyle,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(
                    onClick = { onTest(url, token) },
                    enabled = url.isNotBlank() && !testing,
                ) {
                    Text(if (testing) "جارٍ الاختبار…" else "اختبار الاتصال")
                }
                if (testMessage != null) {
                    Text(
                        text = testMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (testMessage.startsWith("✓")) MaterialTheme.cady.success else MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(url, token) }) { Text("حفظ") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/** A campaign made on the spot from just its name; its dates and goal are set later on the campaigns screen. */
@Composable
fun NewCampaignDialog(onCreate: (String) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حملة جديدة") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("اسم الحملة") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "تُعطى الحملة رمزًا قصيرًا يحمله كل رابط فيها. التواريخ والهدف من شاشة «الحملات».",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = { onCreate(name) }, enabled = name.isNotBlank()) { Text("إنشاء وربط") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

/**
 * Asked when the screen closes with work that would be lost. Saving a draft first is the first choice, since that is
 * what most people mean; it is offered only when there is a text to save.
 */
@Composable
fun CloseConfirmDialog(
    canSave: Boolean,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    onKeep: () -> Unit,
) {
    Dialog(onDismissRequest = onKeep) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("حفظ التعديلات؟", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "لم تحفظ تعديلاتك على هذا المحتوى، وستضيع إن خرجت الآن.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                if (canSave) {
                    Button(onClick = onSave, modifier = Modifier.fillMaxWidth()) { Text("حفظ كمسودة وخروج") }
                }
                OutlinedButton(onClick = onKeep, modifier = Modifier.fillMaxWidth()) { Text("متابعة التحرير") }
                TextButton(
                    onClick = onDiscard,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("تجاهل وخروج") }
            }
        }
    }
}

@Composable
fun DatePickerDialogHost(initialDate: String?, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate?.let { DateUtil.toUtcMillis(it) } ?: DateUtil.toUtcMillis(DateUtil.today()),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val millis = pickerState.selectedDateMillis
                    if (millis != null) onConfirm(DateUtil.fromUtcMillis(millis)) else onDismiss()
                },
            ) { Text("تم") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
fun TimePickerDialogHost(initialTime: String?, onConfirm: (Int, Int) -> Unit, onDismiss: () -> Unit) {
    val parts = initialTime.orEmpty().split(':')
    val pickerState = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 9,
        initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
        is24Hour = false,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("وقت النشر") },
        text = { TimePicker(state = pickerState) },
        confirmButton = { TextButton(onClick = { onConfirm(pickerState.hour, pickerState.minute) }) { Text("تم") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
