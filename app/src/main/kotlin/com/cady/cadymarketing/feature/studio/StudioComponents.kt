package com.cady.cadymarketing.feature.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cady.cadymarketing.core.components.CadyCard
import com.cady.cadymarketing.core.components.IconBadge
import com.cady.cadymarketing.core.components.StatusPill
import com.cady.cadymarketing.core.theme.cady
import com.cady.cadymarketing.domain.engine.RelativeTime
import com.cady.cadymarketing.domain.model.CheckResult
import com.cady.cadymarketing.domain.model.SavedContent

/*
 * Small pieces the Studio's screen, panels and sheets share. They read the style (glass, soft, bold, …) through
 * CadyCard and MaterialTheme, so none of them knows which style is on.
 */

/** The side padding every panel's content shares, so labels, rows and cards line up. */
internal val PanelPadding: Dp = 16.dp

/** A small title above a group of controls inside a panel. */
@Composable
internal fun PanelLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = PanelPadding, end = PanelPadding, top = 14.dp, bottom = 6.dp),
    )
}

/** One quiet line of help under a group of controls. */
@Composable
internal fun PanelHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(horizontal = PanelPadding, vertical = 8.dp),
    )
}

/** The top of a panel: what the tool is, one line on what it does, and (when a tool is open) a button to fold it away. */
@Composable
internal fun ToolHeader(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onCollapse: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBadge(icon = icon, size = 36.dp, iconSize = 19.dp)
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onCollapse != null) {
            IconButton(onClick = onCollapse) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "إخفاء اللوحة")
            }
        }
    }
}

/** A small round marker (on a tool of the bar: "this one has something to look at"). */
@Composable
internal fun StatusDot(color: Color, modifier: Modifier = Modifier, size: Dp = 9.dp) {
    Box(modifier = modifier.size(size).clip(CircleShape).background(color))
}

/** Whether the post passed the check, in one small label: green when it did, red with the count when it did not. */
@Composable
internal fun CheckChip(check: CheckResult, hasText: Boolean, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val extra = MaterialTheme.cady
    val label: String
    val icon: ImageVector?
    val container: Color
    val content: Color
    when {
        !hasText -> {
            label = "لا يوجد نص"
            icon = null
            container = scheme.surfaceContainerHigh
            content = scheme.onSurfaceVariant
        }
        check.passed -> {
            val advice = check.warnings.distinctBy { it.message }.size
            label = if (advice == 0) "اجتاز الفحص" else "اجتاز الفحص · $advice نصيحة"
            icon = Icons.Filled.CheckCircle
            container = extra.success.copy(alpha = 0.14f)
            content = extra.success
        }
        else -> {
            val count = check.errors.distinctBy { it.message }.size
            label = "$count ملاحظة"
            icon = Icons.Filled.Warning
            container = scheme.errorContainer
            content = scheme.onErrorContainer
        }
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}

/** "240/700": the text's length against what the channel suits; it turns amber past the limit (advice only). */
@Composable
internal fun CharCounter(length: Int, limit: Int, modifier: Modifier = Modifier) {
    val over = length > limit
    Text(
        text = "$length/$limit",
        style = MaterialTheme.typography.labelSmall,
        color = if (over) MaterialTheme.cady.warning else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** A tonal button with an icon (or a small spinner while it works), the shape of every action in the text panel. */
@Composable
internal fun ToolActionButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
) {
    FilledTonalButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
        } else {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(text = label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * A saved post as a card: the product, where it stands (draft, waiting, approved…), its text, its channel and how long
 * ago it changed. [trailing] is an optional action at the end (the delete button in the saved list).
 */
@Composable
internal fun SavedPostCard(
    item: SavedContent,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val ago = remember(item.updatedAt) { RelativeTime.ago(System.currentTimeMillis() - item.updatedAt) }
    CadyCard(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = if (trailing == null) 14.dp else 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = item.productName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    StatusPill(item.status)
                }
                Text(
                    text = item.text,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.channel.label + " · " + ago,
                    style = MaterialTheme.typography.labelSmall,
                    color = muted,
                )
            }
            trailing?.invoke()
        }
    }
}

/** Wraps [text] so digits and "×" keep their left-to-right order inside right-to-left text (1080×1920, not 1920×1080). */
internal fun ltr(text: String): String = "\u200E" + text + "\u200E"

/** What a search compares: lower case, no marks, and the Arabic letters people type interchangeably folded together. */
internal fun searchKey(text: String): String = buildString(text.length) {
    for (ch in text.trim().lowercase()) {
        if (ch in '\u064B'..'\u065F') continue
        append(
            when (ch) {
                'أ', 'إ', 'آ' -> 'ا'
                'ة' -> 'ه'
                'ى' -> 'ي'
                else -> ch
            },
        )
    }
}
