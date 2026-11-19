package com.cady.cadymarketing.feature.audience

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.EmptyState
import com.cady.cadymarketing.core.components.Pill
import com.cady.cadymarketing.core.components.SectionTitle
import com.cady.cadymarketing.core.components.StatusPill
import com.cady.cadymarketing.core.theme.cady
import com.cady.cadymarketing.core.util.Clipboard
import com.cady.cadymarketing.domain.model.SavedContent
import com.cady.cadymarketing.domain.model.SendStatus

/**
 * The send list of one post to one segment: each customer who agreed gets their own message with their name, one tap
 * at a time (WhatsApp opens on the customer's chat with the text ready; the person presses send there).
 */
@Composable
fun SendListScreen(
    onBack: () -> Unit,
    viewModel: SendListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var picking by remember { mutableStateOf(false) }
    var showExcluded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SendListEvent.Message -> {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(event.text)
                }
                is SendListEvent.OpenChat -> {
                    if (!openChat(context, event.url)) {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar("لا يوجد تطبيق يفتح رابط واتساب على هذا الجهاز")
                    }
                }
                is SendListEvent.CopyText -> {
                    Clipboard.copy(context, "نص المنشور", event.text)
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar("نُسخ النص")
                }
            }
        }
    }

    CadyScreen(
        title = state.segment?.name ?: "قائمة الإرسال",
        onBack = onBack,
        withBottomInset = true,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            IconButton(onClick = viewModel::copyText, enabled = state.post != null) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "نسخ النص")
            }
        },
    ) { padding ->
        when {
            !state.loaded -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            state.segment == null -> EmptyState(
                icon = Icons.Filled.Warning,
                title = "الشريحة غير موجودة",
                message = "ربما حُذفت. ارجع إلى الجمهور واختر شريحة أخرى.",
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { HeaderCard(state = state) }
                item { PostCard(state = state, onPick = { picking = true }) }
                if (state.overLimit) {
                    item { LimitWarning(count = state.recipients.size) }
                }
                item {
                    val next = state.next
                    Button(
                        onClick = viewModel::sendNext,
                        enabled = state.post != null && next != null,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (next != null) "إرسال إلى " + next.customer.name else "انتهت القائمة")
                    }
                }
                if (state.recipients.isEmpty()) {
                    item {
                        Text(
                            text = "لا أحد في هذه الشريحة يمكن الإرسال إليه الآن. سجّل موافقة العملاء من شاشة الجمهور أولًا.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(state.recipients, key = { it.customer.id }) { recipient ->
                    RecipientRow(
                        recipient = recipient,
                        enabled = state.post != null,
                        onSend = { viewModel.send(recipient.customer.id) },
                        onSkip = { viewModel.skip(recipient.customer.id) },
                        onUndo = { viewModel.undo(recipient.customer.id) },
                    )
                }
                if (state.excluded.isNotEmpty()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showExcluded = !showExcluded },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            SectionTitle(
                                text = "المستبعدون (${state.excluded.size}) " + if (showExcluded) "▲" else "▼",
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    if (showExcluded) {
                        items(state.excluded, key = { "x" + it.first.id }) { (customer, reason) ->
                            CadyCard(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        text = customer.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Pill(reason.label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (picking) {
        PostPickerDialog(
            posts = state.sendable,
            onPick = {
                viewModel.selectPost(it.id)
                picking = false
            },
            onDismiss = { picking = false },
        )
    }
}

private fun openChat(context: Context, url: String): Boolean = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    true
} catch (e: ActivityNotFoundException) {
    false
}

@Composable
private fun HeaderCard(state: SendListState) {
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "${state.progress.done} من ${state.progress.total} تمّ التعامل معهم" +
                    if (state.excluded.isEmpty()) "" else " · ${state.excluded.size} مستبعد",
                style = MaterialTheme.typography.titleSmall,
            )
            val fraction = if (state.progress.total == 0) 0f else state.progress.done.toFloat() / state.progress.total.toFloat()
            LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            if (state.progress.finished) {
                Text(
                    text = "انتهت هذه القائمة. افتح العملاء الباقين من شاشة الجمهور إن أردت زيادة الموافقات.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.cady.success,
                )
            }
        }
    }
}

@Composable
private fun PostCard(state: SendListState, onPick: () -> Unit) {
    val post = state.post
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (post == null) {
                Text("اختر المنشور الذي سيُرسَل", style = MaterialTheme.typography.titleSmall)
                Text(
                    text = if (state.sendable.isEmpty()) {
                        "لا منشور معتمد بعد. اعتمد منشورًا من الاستوديو ثم عُد."
                    } else {
                        "تظهر المنشورات المعتمدة والمنشورة فقط."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(onClick = onPick, enabled = state.sendable.isNotEmpty()) { Text("اختيار المنشور") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = post.productName + " · " + post.channel.label,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    StatusPill(post.status)
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Text(
                        text = state.previewText.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp)
                            .padding(12.dp),
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!state.hasName) {
                    Text(
                        text = "النص لا يحوي {اسم العميل}، فتصل الرسالة نفسها لكل عميل. أضف الاسم من الاستوديو إن أردتها باسمه.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = "الصورة لا تُرفَق بهذه الرسائل: ارسلها مرة واحدة من الاستوديو (مشاركة) إن أردتها مع النص.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = onPick) { Text("تغيير المنشور") }
            }
        }
    }
}

@Composable
private fun LimitWarning(count: Int) {
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.cady.warning)
            Text(
                text = "في القائمة $count عميلًا. إرسال أكثر من 30 رسالة في اليوم من رقم شخصي قد يعرّضه للتقييد من واتساب؛ وزّع الإرسال على أيام (التقدم محفوظ).",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RecipientRow(
    recipient: SendRecipient,
    enabled: Boolean,
    onSend: () -> Unit,
    onSkip: () -> Unit,
    onUndo: () -> Unit,
) {
    val customer = recipient.customer
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = customer.name,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "+" + customer.phone.orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (recipient.status) {
                null -> {
                    FilledTonalButton(onClick = onSend, enabled = enabled) { Text("إرسال") }
                    TextButton(onClick = onSkip) { Text("تخطّي") }
                }
                SendStatus.SENT -> {
                    Pill("أُرسل", background = MaterialTheme.cady.success.copy(alpha = 0.16f), foreground = MaterialTheme.cady.success)
                    TextButton(onClick = onUndo) { Text("تراجع") }
                }
                SendStatus.SKIPPED -> {
                    Pill("تُخطّي")
                    TextButton(onClick = onUndo) { Text("تراجع") }
                }
            }
        }
    }
}

@Composable
private fun PostPickerDialog(posts: List<SavedContent>, onPick: (SavedContent) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("اختر المنشور") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(posts, key = { it.id }) { post ->
                    CadyCard(modifier = Modifier.fillMaxWidth(), onClick = { onPick(post) }) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = post.productName + " · " + post.channel.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                StatusPill(post.status)
                            }
                            Text(
                                text = post.text,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}
