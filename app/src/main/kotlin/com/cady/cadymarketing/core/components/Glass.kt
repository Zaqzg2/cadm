package com.cady.cadymarketing.core.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cady.cadymarketing.core.theme.cady
import kotlinx.coroutines.delay

/**
 * A rounded tile with a white icon over the brand gradient: how the app marks a feature, an action or a figure.
 * The same colours as the sign-in logo, so the identity reads the same on every screen.
 */
@Composable
fun IconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    iconSize: Dp = 22.dp,
) {
    val extra = MaterialTheme.cady
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.34f))
            .background(Brush.linearGradient(listOf(extra.heroStart, extra.heroEnd))),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = extra.onHero, modifier = Modifier.size(iconSize))
    }
}

/**
 * Fades its content in and lifts it a few dp, [index] steps after the screen opened, so a list of cards arrives one
 * after another instead of all at once. It plays once: the "already shown" flag is saved, so scrolling an item back
 * into view, returning to the tab or turning the phone does not play it again.
 */
@Composable
fun Appear(
    index: Int,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    var entered by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!entered) {
            delay((index * STAGGER_MS).coerceAtMost(MAX_DELAY_MS))
            entered = true
        }
    }
    val shownAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "appearAlpha",
    )
    val shownRise by animateFloatAsState(
        targetValue = if (entered) 0f else RISE_DP,
        animationSpec = tween(durationMillis = 320),
        label = "appearRise",
    )
    Box(
        modifier = modifier.graphicsLayer {
            this.alpha = shownAlpha
            translationY = shownRise * density
            // Fade each drawing step on its own instead of through an off-screen copy of the box, so a card's shadow,
            // which falls outside its bounds, is not cut off while the card is fading in.
            compositingStrategy = CompositingStrategy.ModulateAlpha
        },
    ) { content() }
}

private const val STAGGER_MS = 45L
private const val MAX_DELAY_MS = 360L
private const val RISE_DP = 16f
