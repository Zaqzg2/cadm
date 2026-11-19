package com.cady.cadymarketing.feature.library

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.ConfirmDialog
import com.cady.cadymarketing.core.components.EmptyState
import com.cady.cadymarketing.core.components.Pill
import com.cady.cadymarketing.core.components.StatusPill
import com.cady.cadymarketing.core.util.Clipboard
import com.cady.cadymarketing.domain.engine.LibraryRules
import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.SavedContent

/** Every post saved from the Studio: search it, filter it by status, open it for editing, move it between statuses. */
@Composable
fun LibraryScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onSendToAudience: (String) -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var query by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<SavedContent?>(null) }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { text ->
            snackbar.currentSnackbarData?.dismiss()
            snackbar.showSnackbar(text)
        }
    }

    CadyScreen(
        title = "المكتبة",
        onBack = onBack,
        withBottomInset = true,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    viewModel.setQuery(it)
                },
                placeholder = { Text("ابحث في النص أو الصنف أو رمز الحملة") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = state.filter == null,
                    onClick = { viewModel.setFilter(null) },
                    label = { Text("الكل (${state.all.size})") },
                )
                ContentStatus.entries.forEach { status ->
                    FilterChip(
                        selected = state.filter == status,
                        onClick = { viewModel.setFilter(status) },
                        label = { Text("${status.label} (${state.counts[status] ?: 0})") },
                    )
                }
            }
            val visible = state.visible
            when {
                !state.loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                visible.isEmpty() -> EmptyState(
                    icon = Icons.Filled.PhotoLibrary,
                    title = if (state.all.isEmpty()) "المكتبة فارغة" else "لا نتائج",
                    message = if (state.all.isEmpty()) {
                        "ما تحفظه من الاستوديو يظهر هنا، لتعدّله أو تعيد استعماله."
                    } else {
                        "غيّر كلمة البحث أو اختر حالة أخرى."
                    },
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = { it.id }) { post ->
                        PostCard(
                            post = post,
                            onOpen = { onOpenPost(post.id) },
                            onCopy = {
                                Clipboard.copy(context, "نص المنشور", post.text)
                            },
                            onDuplicate = { viewModel.duplicate(post.id) },
                            onSend = { onSendToAudience(post.id) },
                            onMove = { target -> viewModel.move(post.id, target) },
                            onDelete = { pendingDelete = post },
                        )
                    }
                }
            }
        }
    }

    pendingDelete?.let { post ->
        ConfirmDialog(
            title = "حذف المنشور",
            message = "يُحذف منشور «${post.productName}» من المكتبة.",
            confirmLabel = "حذف",
            onConfirm = {
                viewModel.delete(post.id)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun PostCard(
    post: SavedContent,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onDuplicate: () -> Unit,
    onSend: () -> Unit,
    onMove: (ContentStatus) -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    CadyCard(modifier = Modifier.fillMaxWidth(), onClick = onOpen) {
        Column(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 12.dp, end = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = post.productName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                StatusPill(post.status)
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "خيارات المنشور")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("فتح للتعديل") },
                            onClick = {
                                menuOpen = false
                                onOpen()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("نسخ النص") },
                            onClick = {
                                menuOpen = false
                                onCopy()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("نسخة جديدة كمسودة") },
                            onClick = {
                                menuOpen = false
                                onDuplicate()
                            },
                        )
                        if (post.status == ContentStatus.APPROVED || post.status == ContentStatus.PUBLISHED) {
                            DropdownMenuItem(
                                text = { Text("إرسال لقائمة عملاء") },
                                onClick = {
                                    menuOpen = false
                                    onSend()
                                },
                            )
                        }
                        HorizontalDivider()
                        LibraryRules.moves(post.status).forEach { move ->
                            DropdownMenuItem(
                                text = { Text(move.label) },
                                onClick = {
                                    menuOpen = false
                                    onMove(move.target)
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("حذف") },
                            onClick = {
                                menuOpen = false
                                onDelete()
                            },
                        )
                    }
                }
            }
            Text(
                text = post.text,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 10.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Pill(text = post.channel.label)
                post.scheduledAt?.let { Pill(text = it.replace('T', ' ')) }
                post.campaignCode?.let { Pill(text = "حملة $it") }
            }
        }
    }
}
