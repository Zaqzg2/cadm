package com.cady.cadymarketing.core.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The look of the app's cards, chosen in Settings (المظهر) and kept on the device. Every list item and section card goes
 * through [com.cady.cadymarketing.core.components.CadyCard], so changing the style restyles every screen at once.
 * [code] is what is stored: never rename one.
 */
enum class CadyStyle(val code: String, val label: String, val note: String) {
    FLAT("flat", "الحالي", "بطاقات بيضاء بحدّ رفيع"),
    BENTO("bento", "Bento", "بطاقات بلون العلامة وزوايا كبيرة"),
    SAND("sand", "رملي", "سطح مطفي بلون رملي دافئ"),
    BRUTAL("brutal", "Neo-brutalism", "حدود سميكة وظلّ صلب"),
    ;

    companion object {
        /** The style a fresh install starts with. */
        val DEFAULT = BENTO

        fun fromCode(code: String?): CadyStyle = entries.firstOrNull { it.code == code } ?: DEFAULT
    }
}

val LocalCadyStyle = staticCompositionLocalOf { CadyStyle.DEFAULT }

/** The chosen card style, read with `MaterialTheme.cadyStyle`. */
val MaterialTheme.cadyStyle: CadyStyle
    @Composable
    @ReadOnlyComposable
    get() = LocalCadyStyle.current

/** What a card looks like in one style. [shadow] is the offset of a hard, blur-free shadow (0 = none). */
@Immutable
class CardLook(
    val shape: Shape,
    val container: Color,
    val border: BorderStroke?,
    val radius: Dp,
    val shadow: Dp = 0.dp,
    val shadowColor: Color = Color.Transparent,
)

/** The card look of [style] in the current colour scheme, so it follows the brand colour and the dark theme. */
@Composable
fun cardLook(style: CadyStyle): CardLook {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    return when (style) {
        CadyStyle.FLAT -> CardLook(
            shape = MaterialTheme.shapes.medium,
            container = scheme.surfaceContainerLowest,
            border = BorderStroke(1.dp, scheme.outlineVariant),
            radius = 14.dp,
        )
        CadyStyle.BENTO -> CardLook(
            shape = RoundedCornerShape(24.dp),
            container = lerp(scheme.surfaceContainerLowest, scheme.primaryContainer, if (dark) 0.25f else 0.35f),
            border = null,
            radius = 24.dp,
        )
        CadyStyle.SAND -> {
            val sand = lerp(
                scheme.surfaceContainerLowest,
                if (dark) Color(0xFF5A4B33) else Color(0xFFE8D6B8),
                0.6f,
            )
            CardLook(
                shape = RoundedCornerShape(20.dp),
                container = sand,
                border = BorderStroke(1.dp, lerp(sand, scheme.onSurface, 0.12f)),
                radius = 20.dp,
            )
        }
        CadyStyle.BRUTAL -> CardLook(
            shape = RoundedCornerShape(10.dp),
            container = scheme.surfaceContainerLowest,
            border = BorderStroke(2.dp, scheme.onSurface),
            radius = 10.dp,
            shadow = 4.dp,
            shadowColor = scheme.onSurface,
        )
    }
}

/** A solid rounded block drawn [offset] down and to the right behind the element: the Neo-brutalism shadow. */
fun Modifier.hardShadow(color: Color, offset: Dp, radius: Dp): Modifier = drawBehind {
    val o = offset.toPx()
    drawRoundRect(color = color, topLeft = Offset(o, o), size = size, cornerRadius = CornerRadius(radius.toPx()))
}
