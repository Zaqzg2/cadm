package com.cady.cadymarketing.feature.campaigns

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.cady.cadymarketing.core.role.canUse
import com.cady.cadymarketing.domain.engine.Feature
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.ConfirmDialog
import com.cady.cadymarketing.core.components.EmptyState
import com.cady.cadymarketing.core.components.LabeledValue
import com.cady.cadymarketing.core.components.PhasePill
import com.cady.cadymarketing.core.components.Pill
import com.cady.cadymarketing.core.components.SectionTitle
import com.cady.cadymarketing.core.components.StatusPill
import com.cady.cadymarketing.core.util.Clipboard
import com.cady.cadymarketing.core.util.ShareImage
import com.cady.cadymarketing.domain.engine.DateUtil
import com.cady.cadymarketing.domain.engine.TrackingLink
import com.cady.cadymarketing.domain.engine.CampaignStockRow
import com.cady.cadymarketing.domain.engine.InventoryRules
import com.cady.cadymarketing.domain.model.Campaign
import com.cady.cadymarketing.domain.model.CampaignStatus
import com.cady.cadymarketing.domain.model.ContentStatus
import com.cady.cadymarketing.domain.model.SavedContent

/**
 * One campaign: its dates and status, a tracking link and a QR for every channel, and the posts that carry its code.
 * [onNewPost] opens the Studio already linked to this campaign.
 */
@Composable
fun CampaignDetailScreen(
    onBack: () -> Unit,
    onOpenPost: (String) -> Unit,
    onNewPost: (String) -> Unit,
    viewModel: CampaignDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    var showEdit by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is CampaignDetailEvent.Message -> {
                    snackbar.currentSnackbarData?.dismiss()
                    snackbar.showSnackbar(event.text)
                }
                is CampaignDetailEvent.Share -> {
                    if (!ShareImage.send(context, event.uri, title = "مشاركة رمز QR")) {
                        snackbar.showSnackbar("لا يوجد تطبيق للمشاركة")
                    }
                }
                CampaignDetailEvent.Deleted -> onBack()
            }
        }
    }

    val campaign = state.campaign
    val canManage = canUse(Feature.MANAGE_CAMPAIGNS)
    CadyScreen(
        title = campaign?.name ?: "الحملة",
        onBack = onBack,
        withBottomInset = true,
        snackbarHost = { SnackbarHost(snackbar) },
        actions = {
            if (campaign != null && canManage) {
                IconButton(onClick = { showEdit = true }) {
                    Icon(Icons.Filled.Edit, contentDescription = "تعديل")
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "المزيد")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        statusMoves(campaign.status).forEach { (label, target) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    menuOpen = false
                                    viewModel.setStatus(target)
                                },
                            )
                        }
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("حذف الحملة") },
                            onClick = {
                                menuOpen = false
                                showDelete = true
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        when {
            !state.loaded -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            campaign == null -> EmptyState(
                icon = Icons.Filled.Campaign,
                title = "هذه الحملة لم تعد موجودة",
                message = "ربما حُذفت. ارجع إلى قائمة الحملات.",
                modifier = Modifier.padding(padding),
                actionLabel = "رجوع",
                onAction = onBack,
            )
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { SummaryCard(campaign, state.posts) }
                if (state.stockRows.isNotEmpty()) {
                    item { SectionTitle("توفر أصناف الحملة") }
                    items(state.stockRows, key = { it.productId }) { row -> StockRowCard(row) }
                }
                item { SectionTitle("روابط التتبّع ورمز QR") }
                items(state.links, key = { it.channel.code }) { link ->
                    LinkRow(
                        link = link,
                        onCopy = {
                            Clipboard.copy(context, "رابط الحملة", link.url)
                        },
                        onQr = { viewModel.showQr(link) },
                    )
                }
                item {
                    Text(
                        text = "ضع الرابط في المنشور أو اطبع الـQR على الورق: كل من يستعمله يُنسب طلبه إلى هذه الحملة.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item { SectionTitle("منشورات الحملة (${state.posts.size})") }
                item {
                    Button(onClick = { onNewPost(campaign.id) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("منشور جديد لهذه الحملة")
                    }
                }
                items(state.posts, key = { it.id }) { post ->
                    PostRow(post = post, onClick = { onOpenPost(post.id) })
                }
            }
        }
    }

    if (showEdit && campaign != null) {
        CampaignFormDialog(
            initial = campaign,
            onSave = { name, goal, start, end ->
                viewModel.update(name, goal, start, end)
                showEdit = false
            },
            onDismiss = { showEdit = false },
        )
    }
    if (showDelete) {
        ConfirmDialog(
            title = "حذف الحملة",
            message = "تختفي الحملة من القوائم، وتبقى منشوراتها وروابطها المنشورة كما هي، ولا يُعاد استعمال رمزها.",
            confirmLabel = "حذف",
            onConfirm = {
                showDelete = false
                viewModel.delete()
            },
            onDismiss = { showDelete = false },
        )
    }
    state.qr?.let { preview ->
        QrDialog(
            preview = preview,
            onCopy = {
                Clipboard.copy(context, "رابط الحملة", preview.link)
            },
            onSave = viewModel::saveQr,
            onShare = viewModel::shareQr,
            onDismiss = viewModel::hideQr,
        )
    }
}

/** The status changes that make sense from the current status. */
private fun statusMoves(current: CampaignStatus): List<Pair<String, CampaignStatus>> = when (current) {
    CampaignStatus.ACTIVE -> listOf("إيقاف مؤقت" to CampaignStatus.PAUSED, "إنهاء الحملة" to CampaignStatus.ENDED)
    CampaignStatus.PAUSED -> listOf("استئناف" to CampaignStatus.ACTIVE, "إنهاء الحملة" to CampaignStatus.ENDED)
    CampaignStatus.ENDED -> listOf("إعادة تفعيل" to CampaignStatus.ACTIVE)
}

@Composable
private fun SummaryCard(campaign: Campaign, posts: List<SavedContent>) {
    val today = DateUtil.today()
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "الرمز ${campaign.code}",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                PhasePill(campaign.phase(today))
            }
            LabeledValue("الفترة", campaign.periodText)
            if (campaign.number != null) LabeledValue("الرقم", campaign.number)
            if (campaign.goal.isNotBlank()) {
                Text(
                    text = campaign.goal,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            HorizontalDivider()
            LabeledValue("المنشورات", posts.size.toString())
            LabeledValue("مسودات", posts.count { it.status == ContentStatus.DRAFT }.toString())
            LabeledValue("بانتظار الاعتماد", posts.count { it.status == ContentStatus.PENDING }.toString())
            LabeledValue("معتمدة", posts.count { it.status == ContentStatus.APPROVED }.toString())
            LabeledValue("منشورة", posts.count { it.status == ContentStatus.PUBLISHED }.toString())
        }
    }
}

/** One product of the campaign: what the inventory says about its stock, and the month's goal when there is one. */
@Composable
private fun StockRowCard(row: CampaignStockRow) {
    val info = row.info
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(row.productName, style = MaterialTheme.typography.titleSmall)
                if (info == null) {
                    Text(
                        "لم يُربط هذا الصنف بالمخزون",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    val unit = if (info.unit.isBlank()) "" else " ${info.unit}"
                    Text(
                        "المخزون: ${info.onHand}$unit",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    info.goal?.let {
                        Text(
                            InventoryRules.goalText(it, info.unit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    info.nextArrival?.let {
                        Text(
                            "تصل ${it.qty}$unit في ${it.date}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Pill(text = info?.status?.label ?: "بلا ربط")
        }
    }
}

@Composable
private fun LinkRow(link: TrackingLink, onCopy: () -> Unit, onQr: () -> Unit) {
    CadyCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 6.dp, bottom = 6.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(link.channel.label, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = link.url,
                    style = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onCopy) {
                Icon(Icons.Filled.ContentCopy, contentDescription = "نسخ الرابط")
            }
            IconButton(onClick = onQr) {
                Icon(Icons.Filled.QrCode2, contentDescription = "رمز QR")
            }
        }
    }
}

@Composable
private fun PostRow(post: SavedContent, onClick: () -> Unit) {
    CadyCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
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
            }
            Text(
                text = post.text,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = post.channel.label + (post.scheduledAt?.let { " · " + it.replace('T', ' ') } ?: ""),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
