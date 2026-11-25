package com.cady.cadymarketing.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cady.cadymarketing.core.components.CadyScreen
import com.cady.cadymarketing.core.components.Pill
import com.cady.cadymarketing.core.theme.CadyStyleTokens
import com.cady.cadymarketing.core.theme.LocalCadyStyle
import com.cady.cadymarketing.core.theme.cardShadowFor
import com.cady.cadymarketing.core.theme.styledBackdrop
import com.cady.cadymarketing.domain.model.AppStyle
import com.cady.cadymarketing.domain.model.ThemeMode

/** The styles two to a row, in the order of [AppStyle]. */
private val styleRows = AppStyle.entries.chunked(2)

/**
 * Settings → المظهر. Every style is drawn live in its own tile, with the colours of the current
 * mode and brand, so what is tapped is what the app becomes. The app changes at once: the choice is
 * saved and the whole window watches it.
 */
@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    viewModel: AppearanceViewModel = hiltViewModel(),
) {
    val saved by viewModel.appearance.collectAsStateWithLifecycle()
    val look = LocalCadyStyle.current

    CadyScreen(title = "المظهر", onBack = onBack, withBottomInset = true) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Heading(
                    title = "نمط التطبيق",
                    hint = "يغيّر شكل الخلفية والبطاقات والأشرطة. الألوان تبقى من هوية كادي، والملصقات المصمَّمة لا تتأثر.",
                )
            }
            items(styleRows) { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { style ->
                        StyleTile(
                            style = style,
                            current = look,
                            selected = style == look.style,
                            onClick = { viewModel.setStyle(style) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            item {
                Heading(title = "الوضع", hint = "«تلقائي» يتبع إعداد الهاتف.")
            }
            item {
                ModeSelector(selected = saved?.mode, onSelect = viewModel::setMode)
            }
        }
    }
}

@Composable
private fun Heading(title: String, hint: String) {
    Column(modifier = Modifier.padding(top = 4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** One style: a small picture of the page with two cards, then its name and what sets it apart. */
@Composable
private fun StyleTile(
    style: AppStyle,
    current: CadyStyleTokens,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val preview = remember(current, style) { current.forStyle(style) }
    val app = MaterialTheme.colorScheme
    val frame = RoundedCornerShape(18.dp)

    Column(
        modifier = modifier
            .clip(frame)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(128.dp)
                .clip(frame)
                .styledBackdrop(preview)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) app.primary else app.outlineVariant,
                    shape = frame,
                )
                .padding(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                MiniCard(preview, Modifier.fillMaxWidth().height(40.dp))
                MiniCard(preview, Modifier.fillMaxWidth(0.75f).height(40.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(style.label, style = MaterialTheme.typography.titleSmall)
                    if (style == AppStyle.DEFAULT) Pill(text = "الافتراضي")
                }
                Text(
                    text = style.blurb,
                    style = MaterialTheme.typography.bodySmall,
                    color = app.onSurfaceVariant,
                    minLines = 2,
                    maxLines = 2,
                )
            }
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "النمط الحالي",
                    tint = app.primary,
                )
            }
        }
    }
}

/** A miniature list card drawn the way [look] draws every card: same fill, border and shadows, smaller. */
@Composable
private fun MiniCard(look: CadyStyleTokens, modifier: Modifier = Modifier) {
    val corner = look.cardCorner * 0.5f
    val shape = RoundedCornerShape(corner)
    val brush = look.cardBrush
    var card = modifier
        .cardShadowFor(look, corner = corner, scale = 0.45f)
        .then(if (brush != null) Modifier.background(brush, shape) else Modifier.background(look.cardContainer, shape))
    val border = look.cardBorder
    if (border != null) card = card.border(border, shape)

    Row(
        modifier = card.padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(look.colorScheme.primary, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(
                modifier = Modifier
                    .width(54.dp)
                    .height(5.dp)
                    .background(look.colorScheme.onSurface.copy(alpha = 0.70f), RoundedCornerShape(3.dp)),
            )
            Box(
                modifier = Modifier
                    .width(34.dp)
                    .height(4.dp)
                    .background(look.colorScheme.onSurfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(2.dp)),
            )
        }
    }
}

/** Three joined choices: follow the phone, light, dark. [selected] is null until the saved mode is read. */
@Composable
private fun ModeSelector(selected: ThemeMode?, onSelect: (ThemeMode) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .border(1.dp, scheme.outlineVariant, shape),
    ) {
        ThemeMode.entries.forEach { mode ->
            val on = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(if (on) scheme.primaryContainer else Color.Transparent)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(mode) })
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = mode.label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (on) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
                )
            }
        }
    }
}
