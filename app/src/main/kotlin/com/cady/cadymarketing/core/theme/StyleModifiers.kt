package com.cady.cadymarketing.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * How a style is painted. Everything here is plain drawing (rectangles, circles, gradients), so it
 * works on every Android version the app supports and needs no library.
 */

/** The page, painted once under the whole app; every screen's own frame is see-through. */
fun Modifier.styledBackdrop(look: CadyStyleTokens): Modifier = drawBehind {
    drawRect(look.page)
    val w = size.width
    val h = size.height
    when (look.backdrop) {
        Backdrop.FLAT -> Unit
        Backdrop.BLOBS -> {
            glow(look.glowA, Offset(w * 0.10f, h * 0.10f), w * 0.95f)
            glow(look.glowB, Offset(w * 0.95f, h * 0.45f), w * 0.85f)
            glow(look.glowC, Offset(w * 0.20f, h * 0.95f), w * 0.90f)
        }
        Backdrop.WASH -> {
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(look.glowA, look.glowA.copy(alpha = 0f)),
                    startY = 0f,
                    endY = h * 0.55f,
                ),
                size = Size(w, h * 0.55f),
            )
            glow(look.glowB, Offset(w * 0.90f, h * 0.04f), w * 0.80f)
        }
    }
}

/**
 * What sits behind a card, drawn outside its bounds. [corner] and [scale] let the picker draw a
 * miniature card with the same shadows at a smaller size.
 */
fun Modifier.cardShadowFor(look: CadyStyleTokens, corner: Dp = look.cardCorner, scale: Float = 1f): Modifier =
    when (look.cardShadow) {
        CardShadow.NONE -> this
        CardShadow.HARD -> drawBehind {
            val d = look.shadowOffset.toPx() * scale
            drawRoundRect(
                color = look.shadowColor,
                topLeft = Offset(d, d),
                size = size,
                cornerRadius = CornerRadius(corner.toPx()),
            )
        }
        CardShadow.RAISED -> drawBehind {
            val d = look.shadowOffset.toPx() * scale
            val r = corner.toPx()
            stackedShadow(look.shadowColor, d, d, d * 2f, r)
            stackedShadow(look.highlightColor, -d, -d, d * 2f, r)
        }
    }

/** The bottom bar's outline: rounded top corners with a lift, and a line along its top edge. */
fun Modifier.barLook(look: CadyStyleTokens): Modifier {
    var result: Modifier = this
    if (look.barTopCorner > 0.dp || look.barLift > 0.dp) {
        result = result.shadow(
            elevation = look.barLift,
            shape = RoundedCornerShape(topStart = look.barTopCorner, topEnd = look.barTopCorner),
            clip = true,
        )
    }
    if (look.barLineWidth > 0.dp) {
        result = result.drawWithContent {
            drawContent()
            val w = look.barLineWidth.toPx()
            drawLine(
                color = look.barLine,
                start = Offset(0f, w / 2f),
                end = Offset(size.width, w / 2f),
                strokeWidth = w,
            )
        }
    }
    return result
}

/** A line along the top bar's bottom edge, for the styles that draw one. */
fun Modifier.topBarLook(look: CadyStyleTokens): Modifier {
    if (look.topBarLineWidth <= 0.dp) return this
    return drawWithContent {
        drawContent()
        val w = look.topBarLineWidth.toPx()
        drawLine(
            color = look.topBarLine,
            start = Offset(0f, size.height - w / 2f),
            end = Offset(size.width, size.height - w / 2f),
            strokeWidth = w,
        )
    }
}

/**
 * A soft round glow: [color] at the centre fading to nothing at [radius]. It fades to the same colour
 * with no alpha, not to Color.Transparent (black with no alpha), which would grey the glow on the way out.
 */
private fun DrawScope.glow(color: Color, center: Offset, radius: Float) {
    if (color.alpha <= 0f) return
    drawCircle(
        brush = Brush.radialGradient(colors = listOf(color, color.copy(alpha = 0f)), center = center, radius = radius),
        radius = radius,
        center = center,
    )
}

/**
 * A blurred shadow without a blur: the card's outline drawn [LAYERS] times, each a little larger and
 * fainter, which adds up to a soft falloff. The card itself covers the middle, so only the edge shows.
 */
private fun DrawScope.stackedShadow(color: Color, dx: Float, dy: Float, spread: Float, corner: Float) {
    if (color.alpha <= 0f) return
    for (i in 0 until LAYERS) {
        val t = i / (LAYERS - 1f)
        val grow = spread * t
        val fade = (1f - t) * (1f - t)
        drawRoundRect(
            color = color.copy(alpha = (color.alpha * 0.5f * fade).coerceIn(0f, 1f)),
            topLeft = Offset(dx - grow, dy - grow),
            size = Size(size.width + grow * 2f, size.height + grow * 2f),
            cornerRadius = CornerRadius(corner + grow),
        )
    }
}

private const val LAYERS = 8
