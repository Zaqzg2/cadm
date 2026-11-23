package com.cady.cadymarketing.core.theme

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cady.cadymarketing.domain.model.AppStyle

/** How the page behind every screen is painted. */
enum class Backdrop {
    /** One flat colour. */
    FLAT,

    /** Three soft colour glows, so translucent cards have something to be see-through over. */
    BLOBS,

    /** A wash of the brand colour fading down from the top edge. */
    WASH,
}

/** What sits behind a card. */
enum class CardShadow {
    NONE,

    /** Two soft shadows, a dark one below and a light one above: a surface pushed out of the page. */
    RAISED,

    /** One solid block offset down and to the side, like a printed label. */
    HARD,
}

/**
 * Everything a style decides, worked out once from the brand colour scheme ([base]). The page, the
 * cards, the top bar and the bottom bar all read these, so a screen that uses `CadyScreen` and
 * `CadyCard` takes on a style without knowing about it.
 *
 * [colorScheme] is [base] with this style's few changes (page and card colours, border colours);
 * it is what Material components see. [forStyle] dresses the same colours in another style, which is
 * how the picker draws a live preview of every style at once.
 */
@Immutable
class CadyStyleTokens(
    val style: AppStyle,
    val dark: Boolean,
    val base: ColorScheme,
    val extra: CadyExtraColors,
    val colorScheme: ColorScheme,
    val shapes: Shapes,
    val typography: Typography,
    // The page.
    val page: Color,
    // Cards.
    val cardCorner: Dp,
    val cardContainer: Color,
    val cardBorder: BorderStroke?,
    // The bottom bar.
    val barContainer: Color,
    val barIndicator: Color,
    // The rest is optional: a style only says what it uses.
    val backdrop: Backdrop = Backdrop.FLAT,
    val glowA: Color = Color.Transparent,
    val glowB: Color = Color.Transparent,
    val glowC: Color = Color.Transparent,
    val cardElevation: Dp = 0.dp,
    val cardShadow: CardShadow = CardShadow.NONE,
    val shadowColor: Color = Color.Transparent,
    val highlightColor: Color = Color.Transparent,
    val shadowOffset: Dp = 0.dp,
    val topBarLine: Color = Color.Transparent,
    val topBarLineWidth: Dp = 0.dp,
    /** Icon and label colour of the selected tab; unspecified keeps Material's own. */
    val barSelected: Color = Color.Unspecified,
    val barLine: Color = Color.Transparent,
    val barLineWidth: Dp = 0.dp,
    val barTopCorner: Dp = 0.dp,
    val barLift: Dp = 0.dp,
) {
    /** The same colours dressed in another style: the live previews in Settings → المظهر. */
    fun forStyle(other: AppStyle): CadyStyleTokens = buildStyleTokens(other, base, extra, dark)
}

/** The style in force; provided by [CadyMarketingTheme]. */
val LocalCadyStyle = staticCompositionLocalOf {
    buildStyleTokens(AppStyle.CLASSIC, LightColorScheme, LightExtraColors, dark = false)
}

/** Works out [style] for the brand scheme [base] in light or dark. */
fun buildStyleTokens(
    style: AppStyle,
    base: ColorScheme,
    extra: CadyExtraColors,
    dark: Boolean,
): CadyStyleTokens = when (style) {
    AppStyle.CLASSIC -> classic(base, extra, dark)
    AppStyle.GLASS -> glass(base, extra, dark)
    AppStyle.SOFT -> soft(base, extra, dark)
    AppStyle.MINIMAL -> minimal(base, extra, dark)
    AppStyle.GRADIENT -> gradient(base, extra, dark)
    AppStyle.BOLD -> bold(base, extra, dark)
}

/** The look the app always had: white cards, a thin border, the page colour behind. */
private fun classic(base: ColorScheme, extra: CadyExtraColors, dark: Boolean) = CadyStyleTokens(
    style = AppStyle.CLASSIC,
    dark = dark,
    base = base,
    extra = extra,
    colorScheme = base,
    shapes = CadyShapes,
    typography = CadyTypography,
    page = base.background,
    cardCorner = 14.dp,
    cardContainer = base.surfaceContainerLowest,
    cardBorder = BorderStroke(1.dp, base.outlineVariant),
    barContainer = base.surfaceContainer,
    barIndicator = base.secondaryContainer,
)

/**
 * Frosted glass. The cards are see-through with a bright edge, over soft glows in the brand colour,
 * the info blue and the amber: three tints, like light on a soap film. The blur is imitated (a
 * translucent fill over smooth glows), so it works on every Android version with no library.
 */
private fun glass(base: ColorScheme, extra: CadyExtraColors, dark: Boolean): CadyStyleTokens {
    val page = if (dark) lerp(base.background, Color.Black, 0.35f) else lerp(base.background, Color.White, 0.5f)
    val edgeStart = Color.White.copy(alpha = if (dark) 0.38f else 0.95f)
    val edgeEnd = if (dark) Color.White.copy(alpha = 0.06f) else base.outline.copy(alpha = 0.32f)
    return CadyStyleTokens(
        style = AppStyle.GLASS,
        dark = dark,
        base = base,
        extra = extra,
        colorScheme = base.copy(background = page, surface = page),
        shapes = shapesOf(10, 14, 22, 26, 34),
        typography = CadyTypography,
        page = page,
        cardCorner = 22.dp,
        cardContainer = Color.White.copy(alpha = if (dark) 0.08f else 0.60f),
        cardBorder = BorderStroke(1.dp, Brush.linearGradient(listOf(edgeStart, edgeEnd))),
        barContainer = if (dark) base.surfaceContainerLow.copy(alpha = 0.72f) else Color.White.copy(alpha = 0.74f),
        barIndicator = base.primaryContainer.copy(alpha = if (dark) 0.85f else 0.90f),
        backdrop = Backdrop.BLOBS,
        glowA = base.primary.copy(alpha = if (dark) 0.28f else 0.22f),
        glowB = extra.info.copy(alpha = if (dark) 0.26f else 0.20f),
        glowC = base.tertiary.copy(alpha = 0.22f),
        barLine = if (dark) Color.White.copy(alpha = 0.14f) else base.outline.copy(alpha = 0.18f),
        barLineWidth = 1.dp,
    )
}

/** Soft UI: cards are the page's own colour, pushed out by a dark and a light shadow, with big corners. */
private fun soft(base: ColorScheme, extra: CadyExtraColors, dark: Boolean): CadyStyleTokens {
    val surface = if (dark) base.surfaceContainer else lerp(base.background, base.surfaceVariant, 0.55f)
    return CadyStyleTokens(
        style = AppStyle.SOFT,
        dark = dark,
        base = base,
        extra = extra,
        colorScheme = base.copy(background = surface, surface = surface, surfaceContainerLowest = surface),
        shapes = shapesOf(10, 16, 24, 28, 36),
        typography = CadyTypography,
        page = surface,
        cardCorner = 24.dp,
        cardContainer = surface,
        cardBorder = null,
        barContainer = surface,
        barIndicator = base.primaryContainer,
        cardShadow = CardShadow.RAISED,
        shadowColor = if (dark) Color.Black.copy(alpha = 0.70f) else base.outline.copy(alpha = 0.55f),
        highlightColor = if (dark) Color.White.copy(alpha = 0.07f) else Color.White,
        shadowOffset = 6.dp,
        barTopCorner = 24.dp,
        barLift = 8.dp,
    )
}

/** Minimal: a pure page, cards with no fill and a hairline, tight corners, lighter headings. */
private fun minimal(base: ColorScheme, extra: CadyExtraColors, dark: Boolean): CadyStyleTokens {
    val page = if (dark) Color(0xFF0B0B0C) else Color.White
    return CadyStyleTokens(
        style = AppStyle.MINIMAL,
        dark = dark,
        base = base,
        extra = extra,
        colorScheme = base.copy(background = page, surface = page, surfaceContainerLowest = page),
        shapes = shapesOf(4, 6, 12, 14, 20),
        typography = quietTypography,
        page = page,
        cardCorner = 12.dp,
        cardContainer = Color.Transparent,
        cardBorder = BorderStroke(1.dp, base.outlineVariant),
        barContainer = page,
        barIndicator = Color.Transparent,
        barSelected = base.primary,
        topBarLine = base.outlineVariant,
        topBarLineWidth = 1.dp,
        barLine = base.outlineVariant,
        barLineWidth = 1.dp,
    )
}

/** Gradient: a wash of the brand colour from the top, white rounded cards that float on a soft shadow. */
private fun gradient(base: ColorScheme, extra: CadyExtraColors, dark: Boolean) = CadyStyleTokens(
    style = AppStyle.GRADIENT,
    dark = dark,
    base = base,
    extra = extra,
    colorScheme = base,
    shapes = shapesOf(8, 14, 28, 30, 36),
    typography = CadyTypography,
    page = base.background,
    cardCorner = 28.dp,
    cardContainer = base.surfaceContainerLowest,
    cardBorder = null,
    barContainer = base.surfaceContainerLowest,
    barIndicator = base.primaryContainer,
    backdrop = Backdrop.WASH,
    glowA = if (dark) base.primaryContainer.copy(alpha = 0.75f) else base.primary.copy(alpha = 0.22f),
    glowB = extra.heroEnd.copy(alpha = if (dark) 0.40f else 0.12f),
    cardElevation = 3.dp,
    barTopCorner = 28.dp,
    barLift = 10.dp,
)

/** Bold: a thick ink border, a hard offset shadow, square corners and heavy headings, like a printed label. */
private fun bold(base: ColorScheme, extra: CadyExtraColors, dark: Boolean): CadyStyleTokens {
    val ink = if (dark) Color(0xFFF2EDE4) else Color(0xFF17120F)
    val page = if (dark) lerp(base.background, Color.Black, 0.30f) else lerp(base.background, base.primaryContainer, 0.50f)
    val card = if (dark) base.surfaceContainerHigh else base.surfaceContainerLowest
    return CadyStyleTokens(
        style = AppStyle.BOLD,
        dark = dark,
        base = base,
        extra = extra,
        // Every outline and divider becomes ink, so text fields, chips and outlined buttons agree with the cards.
        colorScheme = base.copy(background = page, surface = page, outline = ink, outlineVariant = ink),
        shapes = shapesOf(2, 4, 6, 8, 12),
        typography = heavyTypography,
        page = page,
        cardCorner = 6.dp,
        cardContainer = card,
        cardBorder = BorderStroke(2.dp, ink),
        barContainer = card,
        barIndicator = base.primaryContainer,
        cardShadow = CardShadow.HARD,
        shadowColor = ink,
        shadowOffset = 4.dp,
        topBarLine = ink,
        topBarLineWidth = 2.dp,
        barLine = ink,
        barLineWidth = 2.dp,
    )
}

private fun shapesOf(extraSmall: Int, small: Int, medium: Int, large: Int, extraLarge: Int) = Shapes(
    extraSmall = RoundedCornerShape(extraSmall.dp),
    small = RoundedCornerShape(small.dp),
    medium = RoundedCornerShape(medium.dp),
    large = RoundedCornerShape(large.dp),
    extraLarge = RoundedCornerShape(extraLarge.dp),
)

/** Headings one step heavier than the app's usual. */
private val heavyTypography = Typography(
    headlineMedium = CadyTypography.headlineMedium.copy(fontWeight = FontWeight.ExtraBold),
    headlineSmall = CadyTypography.headlineSmall.copy(fontWeight = FontWeight.ExtraBold),
    titleLarge = CadyTypography.titleLarge.copy(fontWeight = FontWeight.ExtraBold),
    titleMedium = CadyTypography.titleMedium.copy(fontWeight = FontWeight.Bold),
    titleSmall = CadyTypography.titleSmall.copy(fontWeight = FontWeight.Bold),
    labelLarge = CadyTypography.labelLarge,
)

/** Headings one step lighter than the app's usual. */
private val quietTypography = Typography(
    headlineMedium = CadyTypography.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = CadyTypography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = CadyTypography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = CadyTypography.titleMedium.copy(fontWeight = FontWeight.Medium),
    titleSmall = CadyTypography.titleSmall.copy(fontWeight = FontWeight.Medium),
    labelLarge = CadyTypography.labelLarge,
)
