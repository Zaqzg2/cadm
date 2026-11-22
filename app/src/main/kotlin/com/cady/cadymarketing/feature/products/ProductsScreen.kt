package com.cady.cadymarketing.feature.products

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.EmptyState
import com.cady.cadymarketing.core.components.LabeledValue
import com.cady.cadymarketing.core.role.canUse
import com.cady.cadymarketing.domain.engine.Feature
import com.cady.cadymarketing.core.components.LocalPicture
import com.cady.cadymarketing.core.components.Pill
import com.cady.cadymarketing.data.catalog.RefreshReport
import com.cady.cadymarketing.domain.engine.ArabicText
import com.cady.cadymarketing.domain.engine.InventoryRules
import com.cady.cadymarketing.domain.engine.ProductText
import com.cady.cadymarketing.domain.engine.RelativeTime
import com.cady.cadymarketing.domain.model.ProductRow
import com.cady.cadymarketing.domain.model.StockInfo
import com.cady.cadymarketing.domain.model.StockStatus

/**
 * Kady's catalogue as the marketing app sees it. Prices and offers are read-only (they belong to Kady);
 * what is added here is a picture and the marketing features of each product.
 */
@Composable
fun ProductsScreen(onBack: () -> Unit, viewModel: ProductsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var openId by remember { mutableStateOf<String?>(null) }
    var showReport by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { text ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        val id = openId
        if (uri != null && id != null) viewModel.linkPicture(id, uri)
    }

    CadyScreen(
        title = "الأصناف والعروض",
        onBack = onBack,
        withBottomInset = true,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            IconButton(onClick = { showReport = true }) {
                Icon(Icons.Filled.Info, contentDescription = "تشخيص القراءة")
            }
            IconButton(onClick = viewModel::refresh, enabled = !state.refreshing) {
                if (state.refreshing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "تحديث من كادي")
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            SyncLine(state)
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                placeholder = { Text("ابحث باسم الصنف") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
            )
            if (state.hasSnapshot) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(StockFilter.entries.toList()) { option ->
                        FilterChip(
                            selected = state.stockFilter == option,
                            onClick = { viewModel.setStockFilter(option) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }
            if (state.visible.isEmpty()) {
                EmptyState(
                    icon = Icons.Filled.Inventory2,
                    title = if (state.rows.isEmpty()) "لا توجد أصناف بعد" else "لا نتائج",
                    message = if (state.rows.isEmpty()) {
                        "اضغط زر التحديث في الأعلى لقراءة الأصناف والعروض من كادي."
                    } else {
                        "لا صنف باسم «${state.query.trim()}»."
                    },
                    actionLabel = if (state.rows.isEmpty() && !state.isPreview) "تحديث من كادي" else null,
                    onAction = viewModel::refresh,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.visible, key = { it.product.id }) { row ->
                        ProductItem(
                            row = row,
                            stock = state.stock[row.product.id],
                            loader = viewModel::loadThumb,
                            onClick = { openId = row.product.id },
                        )
                    }
                }
            }
        }
    }

    val open = openId?.let { id -> state.rows.firstOrNull { it.product.id == id } }
    if (open != null) {
        ProductDialog(
            row = open,
            stock = state.stock[open.product.id],
            loader = viewModel::loadThumb,
            onPick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onRemovePicture = { viewModel.removePicture(open.product.id) },
            onSaveFeatures = { text -> viewModel.saveFeatures(open.product.id, text) },
            onDismiss = { openId = null },
        )
    }
    if (showReport) {
        ReportDialog(report = state.report, onDismiss = { showReport = false })
    }
}

@Composable
private fun SyncLine(state: ProductsState) {
    val text = when {
        state.isPreview -> "وضع المعاينة: الأصناف تجريبية (Firebase غير مربوط)"
        state.lastSyncAt == null -> "لم تُقرأ الأصناف من كادي بعد"
        else -> "آخر قراءة من كادي: " + RelativeTime.ago(System.currentTimeMillis() - state.lastSyncAt)
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

/** The stock badge of a product: red when it ran out, amber when low, blue for a surplus, quiet when in stock. */
@Composable
private fun StockPill(info: StockInfo) {
    val scheme = MaterialTheme.colorScheme
    val (background, foreground) = when (info.status) {
        StockStatus.OUT -> scheme.errorContainer to scheme.onErrorContainer
        StockStatus.LOW -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        StockStatus.OVER -> scheme.secondaryContainer to scheme.onSecondaryContainer
        StockStatus.OK -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    Pill(text = "المخزون: " + info.status.label, background = background, foreground = foreground)
}

/** The stock of a product in words: how many, where its goal stands, and when more arrives. */
private fun stockText(info: StockInfo): String {
    val unit = if (info.unit.isBlank()) "" else " ${info.unit}"
    val lines = ArrayList<String>()
    lines.add("المخزون: ${info.onHand}$unit (${info.status.label})")
    info.goal?.let { lines.add(InventoryRules.goalText(it, info.unit)) }
    info.nextArrival?.let { lines.add("تصل ${it.qty}$unit في ${it.date}") }
    return lines.joinToString("\n")
}

@Composable
private fun ProductItem(
    row: ProductRow,
    stock: StockInfo?,
    loader: suspend (String) -> android.graphics.Bitmap?,
    onClick: () -> Unit,
) {
    val product = row.product
    CadyCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LocalPicture(
                fileName = product.picturePath,
                loader = loader,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(10.dp)),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = product.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (product.unit.isNotBlank()) {
                    Text(
                        text = product.unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = ArabicText.formatNumber(product.effectivePrice) + " ريال",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (product.hasOffer) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = ArabicText.formatNumber(product.price),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textDecoration = TextDecoration.LineThrough,
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (product.hasOffer) {
                        val until = if (product.offerEnd.isNotEmpty()) " حتى ${product.offerEnd}" else ""
                        Pill(
                            text = "عرض$until",
                            background = MaterialTheme.colorScheme.tertiaryContainer,
                            foreground = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    if (!row.active) {
                        Pill(
                            text = "غير مفعّل في كادي",
                            background = MaterialTheme.colorScheme.errorContainer,
                            foreground = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                    if (product.features.isNotEmpty()) Pill(text = "${product.features.size} مزايا")
                    if (stock != null) StockPill(stock)
                }
            }
        }
    }
}

@Composable
private fun ProductDialog(
    row: ProductRow,
    stock: StockInfo?,
    loader: suspend (String) -> android.graphics.Bitmap?,
    onPick: () -> Unit,
    onRemovePicture: () -> Unit,
    onSaveFeatures: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val product = row.product
    // The features are the manager's text; a marketer reads them (they shape the posts she writes).
    val canEditFeatures = canUse(Feature.EDIT_PRODUCT_FEATURES)
    var features by remember(product.id) { mutableStateOf(ProductText.featuresText(product.features)) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(product.name) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (stock != null) {
                    Text(stockText(stock), style = MaterialTheme.typography.bodyMedium)
                }
                LabeledValue("الوحدة", product.unit.ifBlank { "—" })
                LabeledValue("السعر في كادي", ArabicText.formatNumber(product.price) + " ريال")
                if (product.hasOffer) {
                    LabeledValue("سعر العرض", ArabicText.formatNumber(product.effectivePrice) + " ريال")
                    LabeledValue("ينتهي العرض", product.offerEnd.ifEmpty { "مفتوح" })
                }
                Text(
                    text = "السعر والعرض يأتيان من كادي ولا يُعدَّلان هنا.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("صورة الصنف", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LocalPicture(
                        fileName = product.picturePath,
                        loader = loader,
                        modifier = Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop,
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = onPick) { Text(if (product.picturePath == null) "ربط صورة" else "تغيير الصورة") }
                        if (product.picturePath != null) {
                            OutlinedButton(onClick = onRemovePicture) { Text("إزالة") }
                        }
                    }
                }
                OutlinedTextField(
                    value = features,
                    onValueChange = { features = it },
                    readOnly = !canEditFeatures,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("مزايا تسويقية (ميزة في كل سطر)") },
                    minLines = 3,
                    maxLines = 6,
                )
                Text(
                    text = if (canEditFeatures) {
                        "تُستعمل المزايا في نصوص المنشورات. أي ميزة فيها رقم أو عبارة ممنوعة تُهمل تلقائيًا."
                    } else {
                        "المزايا يكتبها المدير وتصلك بالمزامنة. الصورة تربطها على جهازك أنت."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            if (canEditFeatures) {
                TextButton(
                    onClick = {
                        onSaveFeatures(features)
                        onDismiss()
                    },
                ) { Text("حفظ") }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

/** What the last read from Kady understood, so a field-name mismatch can be seen at once. */
@Composable
private fun ReportDialog(report: RefreshReport?, onDismiss: () -> Unit) {
    val ltr = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تشخيص القراءة من كادي") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (report == null) {
                    Text("لم تُجرَ قراءة في هذه الجلسة. اضغط زر التحديث ثم افتح هذه النافذة.")
                } else {
                    Text("الأصناف المفهومة: ${report.products} · العروض المفهومة: ${report.offers}")
                    if (report.skipped.isNotEmpty()) {
                        Text("تخطّيت ${report.skipped.size}:", style = MaterialTheme.typography.labelLarge)
                        report.skipped.take(MAX_SKIPPED).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    }
                    Text("أول صنف قرأته:", style = MaterialTheme.typography.labelLarge)
                    Text(report.sampleProduct, style = ltr)
                    Text("أول عرض قرأته:", style = MaterialTheme.typography.labelLarge)
                    Text(report.sampleOffer, style = ltr)
                    Text(
                        text = "إن ظهرت أسماء حقول مختلفة عمّا يفهمه التطبيق (مثل الاسم أو السعر)، أرسل لي هذا النص لأضيف الاسم.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("إغلاق") } },
    )
}

private const val MAX_SKIPPED = 8
