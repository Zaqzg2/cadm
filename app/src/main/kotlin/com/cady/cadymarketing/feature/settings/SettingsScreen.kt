package com.cady.cadymarketing.feature.settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.LabeledValue
import com.cady.cadymarketing.core.components.Pill
import com.cady.cadymarketing.core.role.canUse
import com.cady.cadymarketing.core.util.ShareImage
import com.cady.cadymarketing.domain.engine.BackupFile
import com.cady.cadymarketing.data.inventory.InventorySummary
import com.cady.cadymarketing.domain.engine.Feature
import com.cady.cadymarketing.domain.engine.InventoryRules
import com.cady.cadymarketing.domain.engine.RelativeTime
import com.cady.cadymarketing.domain.engine.SyncSpecs
import com.cady.cadymarketing.domain.model.MktRole
import com.cady.cadymarketing.feature.studio.GatewayDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.ui.draw.clip
import com.cady.cadymarketing.core.theme.CadyStyle
import com.cady.cadymarketing.core.theme.cardLook
import com.cady.cadymarketing.core.theme.hardShadow

/**
 * الفريق والإعدادات. One scrolling page of cards, each for one thing: the account and its role, the sync, the notifications,
 * the team (manager only), the backup, the AI gateway, and the door to the operations log.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onTeam: () -> Unit,
    onLog: () -> Unit,
    onInventoryLinks: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var showGateway by remember { mutableStateOf(false) }
    var showDiagnostics by remember { mutableStateOf(false) }
    // Read here: the list below is built outside composition, where a permission cannot be asked.
    val manageTeam = canUse(Feature.MANAGE_TEAM)
    val manageInventory = canUse(Feature.MANAGE_INVENTORY)
    val seeWholeLog = canUse(Feature.SEE_TEAM_LOG)

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }
    LaunchedEffect(Unit) {
        viewModel.shareRequests.collect { uri ->
            val opened = ShareImage.send(context, uri, null, "مشاركة النسخة الاحتياطية", "application/json")
            if (!opened) snackbar.showSnackbar("لا يوجد تطبيق يستقبل الملف")
        }
    }
    // The person may come back from the system's permission page: show what is true now.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshNotifications() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.refreshNotifications()
        if (!granted) scope.launch { snackbar.showSnackbar("لم يُسمح بالتنبيهات. يمكنك السماح بها من إعدادات النظام.") }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.pickRestore(uri)
    }
    val inventoryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importInventory(uri)
    }

    CadyScreen(
        title = "الفريق والإعدادات",
        onBack = onBack,
        snackbarHost = { SnackbarHost(snackbar) },
        withBottomInset = true,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { AccountCard(state, onRecheck = viewModel::recheckRole, onDetails = { showDiagnostics = true }) }
            item { StyleCard(selected = state.uiStyle, onSelect = viewModel::setUiStyle) }
            item { SyncCard(state, onSync = viewModel::syncNow, onAutoSync = viewModel::setAutoSync) }
            item {
                NotificationsCard(
                    state = state,
                    onAllow = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && viewModel.needsNotificationPermission()) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            openNotificationSettings(context)
                        }
                    },
                    onSystemSettings = { openNotificationSettings(context) },
                    onReminders = viewModel::setRemindersOn,
                    onReviewCheck = viewModel::setReviewCheckOn,
                )
            }
            if (manageTeam) {
                item {
                    LinkCard(
                        title = "فريق التسويق",
                        subtitle = "إضافة مسوّق وتفعيل حسابه أو إيقافه",
                        onClick = onTeam,
                    )
                }
            }
            item {
                InventoryCard(
                    summary = state.inventory,
                    working = state.working,
                    canManage = manageInventory,
                    onImport = { inventoryLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    onLinks = onInventoryLinks,
                )
            }
            item {
                BackupCard(
                    state = state,
                    onSave = viewModel::saveBackup,
                    onShare = viewModel::shareBackup,
                    onRestore = { restoreLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                )
            }
            item {
                SectionCard(title = "بوابة الذكاء") {
                    Text(
                        text = if (state.gateway.isConfigured) "مضبوطة على هذا الجهاز" else "غير مضبوطة بعد",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showGateway = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("ضبط البوابة واختبار الاتصال")
                    }
                }
            }
            item {
                LinkCard(
                    title = "سجل العمليات",
                    subtitle = if (seeWholeLog) "ما فعله كل شخص: اعتماد وإرجاع وحذف ومزامنة" else "ما فعلته على هذا الجهاز",
                    onClick = onLog,
                )
            }
        }
    }

    if (showGateway) {
        GatewayDialog(
            config = state.gateway,
            testing = state.gatewayTesting,
            testMessage = state.gatewayTestMessage,
            onTest = viewModel::testGateway,
            onSave = { url, token ->
                viewModel.saveGateway(url, token)
                showGateway = false
            },
            onDismiss = {
                showGateway = false
                viewModel.clearGatewayTest()
            },
        )
    }
    if (showDiagnostics) {
        AlertDialog(
            onDismissRequest = { showDiagnostics = false },
            title = { Text("ما قرأه التطبيق عن حسابك") },
            text = {
                Text(
                    text = state.diagnostics.ifBlank { "لم يُقرأ شيء بعد. اضغط «إعادة التحقق من الدور»." },
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = { TextButton(onClick = { showDiagnostics = false }) { Text("إغلاق") } },
        )
    }
    state.restore?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::cancelRestore,
            title = { Text("استعادة نسخة احتياطية؟") },
            text = { Text(restoreMessage(preview.file, preview.skipped)) },
            confirmButton = { TextButton(onClick = viewModel::confirmRestore) { Text("استعادة") } },
            dismissButton = { TextButton(onClick = viewModel::cancelRestore) { Text("إلغاء") } },
        )
    }
}

// ---------------------------------------------------------------------- the cards
@Composable
private fun AccountCard(state: SettingsUiState, onRecheck: () -> Unit, onDetails: () -> Unit) {
    SectionCard(title = "الحساب والدور") {
        LabeledValue("الحساب", state.email ?: "وضع المعاينة")
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "الدور",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Pill(text = if (state.role == MktRole.NONE) "غير محدد" else state.role.label)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = when (state.role) {
                MktRole.MANAGER -> "تعتمد المنشورات وتديرها: الحملات والهوية والجمهور والتقارير والفريق."
                MktRole.MARKETER -> "تكتب المنشورات وترسلها للاعتماد. يعتمدها المدير أو يرجعها إليك بملاحظة."
                MktRole.NONE -> ""
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!state.preview) {
            Row {
                TextButton(onClick = onRecheck) { Text("إعادة التحقق من الدور") }
                TextButton(onClick = onDetails) { Text("تفاصيل") }
            }
        }
    }
}

@Composable
private fun SyncCard(state: SettingsUiState, onSync: () -> Unit, onAutoSync: (Boolean) -> Unit) {
    val sync = state.sync
    SectionCard(title = "المزامنة") {
        if (state.preview) {
            Text(
                "المزامنة غير متاحة في وضع المعاينة: Firebase غير مربوط في هذا البناء.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val lastAt = sync.lastAt
            val status = when {
                sync.running -> "جارٍ المزامنة…"
                sync.offline -> "لا يوجد اتصال بالإنترنت. ستُرفع التغييرات حين يعود."
                sync.lastError != null -> "تعذّرت آخر مزامنة: " + sync.lastError
                lastAt != null -> "آخر مزامنة " + RelativeTime.ago(System.currentTimeMillis() - lastAt) + " · " + sync.lastSummary.orEmpty()
                else -> "لم تُجرَ مزامنة في هذه الجلسة بعد."
            }
            Text(
                text = status,
                style = MaterialTheme.typography.bodyMedium,
                color = if (sync.lastError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "تغييرات بانتظار الرفع: ${sync.pending}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (sync.reverted > 0) {
                Text(
                    text = "رُدّ ${sync.reverted} تعديل رفضه الخادم: غيّر المدير المنشور قبل رفع تعديلك فاستُبدل بنسخته.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onSync,
                enabled = !sync.running && state.role != MktRole.NONE,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (sync.running) "جارٍ المزامنة…" else "زامن الآن") }
            Spacer(Modifier.height(6.dp))
            SwitchRow(
                title = "مزامنة تلقائية",
                subtitle = "بعد أي تغيير بدقائق قليلة، وعند فتح التطبيق",
                checked = state.autoSync,
                onChange = onAutoSync,
            )
            Text(
                text = "الصور وملف الشعار تبقى على كل جهاز ولا تُرفع. عند تعارض تعديلين على المنشور نفسه يفوز الأحدث.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NotificationsCard(
    state: SettingsUiState,
    onAllow: () -> Unit,
    onSystemSettings: () -> Unit,
    onReminders: (Boolean) -> Unit,
    onReviewCheck: (Boolean) -> Unit,
) {
    SectionCard(title = "التنبيهات") {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "تنبيهات النظام لهذا التطبيق",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Pill(text = if (state.notificationsAllowed) "مسموحة" else "غير مسموحة")
        }
        if (!state.notificationsAllowed) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAllow) { Text("السماح بالتنبيهات") }
                OutlinedButton(onClick = onSystemSettings) { Text("إعدادات النظام") }
            }
        }
        Spacer(Modifier.height(4.dp))
        HorizontalDivider()
        SwitchRow(
            title = "تذكير موعد النشر",
            subtitle = "تنبيه عند حلول موعد منشور معتمد. قد يتأخر بضع دقائق لتوفير البطارية.",
            checked = state.remindersOn,
            onChange = onReminders,
        )
        HorizontalDivider()
        SwitchRow(
            title = "فحص كل 15 دقيقة",
            subtitle = if (state.role == MktRole.MARKETER) "تنبيه حين يرجع المدير منشورًا إليك للتعديل" else "تنبيه حين ينتظر منشور اعتمادك",
            checked = state.reviewCheckOn,
            onChange = onReviewCheck,
        )
    }
}

@Composable
private fun InventoryCard(
    summary: InventorySummary,
    working: Boolean,
    canManage: Boolean,
    onImport: () -> Unit,
    onLinks: () -> Unit,
) {
    SectionCard(title = "محلل المخزون") {
        if (!summary.hasSnapshot) {
            Text(
                text = if (canManage) "لم تُستورد لقطة مخزون بعد. صدّرها من محلل المخزون إلى ملف ثم استوردها هنا." else "ينتظر المدير استيراد لقطة المخزون.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            val made = if (summary.createdAt > 0L) RelativeTime.ago(System.currentTimeMillis() - summary.createdAt) else "في وقت غير معروف"
            Text(
                text = "آخر لقطة" + (if (summary.branch.isBlank()) "" else " (${summary.branch})") + " $made",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${summary.items} صنفًا · مرتبط ${summary.linked} · مقترح ${summary.suggested} · بلا ربط ${summary.unlinked}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (InventoryRules.isStale(summary.ageDays)) {
                Text(
                    text = "اللقطة قديمة (${summary.ageDays} يومًا): استورد لقطة جديدة لتبقى الشارات صحيحة.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        if (canManage) {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onImport, enabled = !working, modifier = Modifier.fillMaxWidth()) { Text("استيراد لقطة مخزون من ملف") }
            if (summary.hasSnapshot) {
                Spacer(Modifier.height(6.dp))
                OutlinedButton(onClick = onLinks, modifier = Modifier.fillMaxWidth()) { Text("ربط الأصناف") }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "للقراءة فقط: لا يكتب التسويق شيئًا في محلل المخزون ولا في كادي. الأسعار من كادي ولا أسعار في ملف المخزون.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun BackupCard(state: SettingsUiState, onSave: () -> Unit, onShare: () -> Unit, onRestore: () -> Unit) {
    SectionCard(title = "النسخ الاحتياطي") {
        Text(
            text = if (state.lastBackupAt > 0L) "آخر نسخة: " + formatTime(state.lastBackupAt) else "لم تُحفظ نسخة بعد.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = onSave, enabled = !state.working, modifier = Modifier.fillMaxWidth()) {
            Text("حفظ نسخة في Download/csdy")
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(onClick = onShare, enabled = !state.working, modifier = Modifier.weight(1f)) { Text("مشاركة النسخة") }
            OutlinedButton(onClick = onRestore, enabled = !state.working, modifier = Modifier.weight(1f)) { Text("استعادة من ملف") }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "تشمل المنشورات والحملات والمناسبات والموافقات والشرائح والنتائج والهوية والأصناف. لا تشمل الصور والشعار ولا سجل العمليات. الاستعادة تدمج ولا تحذف: الأحدث يفوز.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LinkCard(title: String, subtitle: String, onClick: () -> Unit) {
    CadyCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun StyleCard(selected: CadyStyle, onSelect: (CadyStyle) -> Unit) {
    SectionCard(title = "المظهر") {
        Text(
            text = "شكل البطاقات في كل الشاشات. يُحفظ على هذا الجهاز.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            CadyStyle.entries.chunked(2).forEach { pair ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pair.forEach { style ->
                        StyleOption(
                            style = style,
                            selected = style == selected,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(style) },
                        )
                    }
                }
            }
        }
    }
}

/** One style: a small card drawn in that style, its name and a line about it. */
@Composable
private fun StyleOption(style: CadyStyle, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val look = cardLook(style)
    val tile = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(tile)
            .border(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant, tile)
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().padding(end = 5.dp, bottom = 5.dp)) {
            val shadow = if (look.shadow > 0.dp) Modifier.hardShadow(look.shadowColor, 4.dp, look.radius) else Modifier
            Surface(
                modifier = Modifier.fillMaxWidth().height(48.dp).then(shadow),
                shape = look.shape,
                color = look.container,
                border = look.border,
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.width(38.dp).height(6.dp).background(scheme.onSurface, CircleShape))
                    Box(Modifier.width(62.dp).height(6.dp).background(scheme.outline, CircleShape))
                }
            }
        }
        Text(
            text = style.label + if (selected) " ✓" else "",
            style = MaterialTheme.typography.titleSmall,
            color = if (selected) scheme.primary else scheme.onSurface,
        )
        Text(style.note, style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------- small helpers
private fun restoreMessage(file: BackupFile, skipped: Int): String {
    val lines = file.tables.entries
        .filter { it.key in SyncSpecs.backedUp && it.value.isNotEmpty() }
        .joinToString("\n") { (table, rows) -> "• " + tableLabel(table) + ": " + rows.size }
    return "نسخة بتاريخ " + (if (file.createdAt > 0L) formatTime(file.createdAt) else "غير معروف") +
        (if (file.appVersion.isNotBlank()) " (الإصدار ${file.appVersion})" else "") +
        " فيها ${file.total} سجل:\n" + lines.ifBlank { "• لا شيء" } +
        (if (skipped > 0) "\nتعذّرت قراءة $skipped سجل وسيُترك." else "") +
        "\n\nتُدمج بقاعدة المزامنة نفسها: الأحدث يفوز ولا يُحذف شيء. ما يتغيّر هنا يُرفع في المزامنة التالية."
}

private fun tableLabel(table: String): String = when (table) {
    SyncSpecs.CONTENT -> "المنشورات"
    SyncSpecs.CAMPAIGNS -> "الحملات"
    SyncSpecs.OCCASIONS -> "المناسبات"
    SyncSpecs.CONSENT -> "موافقات العملاء"
    SyncSpecs.SEGMENTS -> "الشرائح"
    SyncSpecs.SEND_MARKS -> "قوائم الإرسال"
    SyncSpecs.RESULTS -> "النتائج"
    SyncSpecs.DOCS -> "الهوية والمفردات والكتالوج"
    SyncSpecs.PRODUCT_EXTRA -> "مزايا الأصناف"
    else -> table
}

private fun formatTime(at: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date(at))

private fun openNotificationSettings(context: Context) {
    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    } else {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))
    }
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        // No settings page to open: the person can allow notifications from the system's own settings.
    }
}
