package com.cady.cadymarketing.feature.inventory

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.EmptyState
import com.cady.cadymarketing.core.components.Pill
import com.cady.cadymarketing.domain.model.Product

/**
 * ربط الأصناف (the manager's): which line of the inventory is which Kady product. The app only suggests an exact name match
 * that nothing else could also claim; every link is the manager's confirmation, and a line without one shows no stock badge.
 */
@Composable
fun InventoryLinksScreen(
    onBack: () -> Unit,
    viewModel: InventoryLinksViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var choosing by remember { mutableStateOf<LinkRow?>(null) }

    LaunchedEffect(Unit) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    CadyScreen(title = "ربط الأصناف", onBack = onBack, snackbarHost = { SnackbarHost(snackbar) }, withBottomInset = true) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            if (state.loaded && !state.hasSnapshot) {
                EmptyState(
                    icon = Icons.Filled.Inventory2,
                    title = "لا لقطة مخزون بعد",
                    message = "استورد لقطة المخزون من «الفريق والإعدادات» ثم عُد لربط أصنافها بأصناف كادي.",
                )
            } else {
                SummaryCard(state, onConfirmAll = viewModel::confirmAll)
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(LinkFilter.entries.toList()) { option ->
                        FilterChip(selected = state.filter == option, onClick = { viewModel.setFilter(option) }, label = { Text(option.label) })
                    }
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::setQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    placeholder = { Text("ابحث باسم الصنف أو الباركود أو رقمه") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    singleLine = true,
                )
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(state.visible, key = { it.state.item.key }) { row ->
                        LinkCard(
                            row = row,
                            onConfirm = { viewModel.confirm(row) },
                            onChoose = { choosing = row },
                            onUnlink = { viewModel.unlink(row) },
                        )
                    }
                }
            }
        }
    }

    choosing?.let { row ->
        ProductPickerDialog(
            title = "اختر صنف كادي لـ «${row.state.item.name}»",
            products = state.products,
            onPick = { product ->
                viewModel.choose(row, product)
                choosing = null
            },
            onDismiss = { choosing = null },
        )
    }
}

@Composable
private fun SummaryCard(state: InventoryLinksState, onConfirmAll: () -> Unit) {
    CadyCard(modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text("${state.rows.size} صنفًا في المخزون", style = MaterialTheme.typography.titleMedium)
            Text(
                "مرتبط ${state.linkedCount} · مقترح ${state.suggestedCount} · بلا ربط ${state.unlinkedCount}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "لا يُربط صنفان بتشابه الاسم: يُقترح فقط ما تطابق اسمه تمامًا ولا يدّعيه غيره، وتؤكده أنت.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (state.suggestedCount > 0) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onConfirmAll, modifier = Modifier.fillMaxWidth()) { Text("تأكيد كل المقترحات (${state.suggestedCount})") }
            }
        }
    }
}

@Composable
private fun LinkCard(row: LinkRow, onConfirm: () -> Unit, onChoose: () -> Unit, onUnlink: () -> Unit) {
    val item = row.state.item
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(item.name, style = MaterialTheme.typography.titleSmall)
                    val identity = listOf(item.barcode, item.itemNo, item.unit).filter { it.isNotBlank() }.joinToString(" · ")
                    if (identity.isNotEmpty()) {
                        Text(identity, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Pill(text = if (row.linked) "مرتبط" else if (row.suggested) "مقترح" else "بلا ربط")
            }
            Spacer(Modifier.height(4.dp))
            when {
                row.linked -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("كادي: ${row.productName}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onUnlink) { Text("فك الربط") }
                }
                row.suggested -> Column {
                    Text("المقترح: ${row.suggestedName}", style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(onClick = onConfirm) { Text("تأكيد") }
                        TextButton(onClick = onChoose) { Text("اختيار آخر") }
                    }
                }
                else -> TextButton(onClick = onChoose) { Text("اختيار صنف من كادي") }
            }
        }
    }
}

@Composable
private fun ProductPickerDialog(
    title: String,
    products: List<Product>,
    onPick: (Product) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val shown = products.filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleMedium) },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("ابحث باسم الصنف") },
                    singleLine = true,
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(modifier = Modifier.height(280.dp)) {
                    items(shown, key = { it.id }) { product ->
                        Text(
                            text = product.name,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(product) }
                                .padding(vertical = 12.dp, horizontal = 4.dp),
                        )
                        HorizontalDivider()
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}
