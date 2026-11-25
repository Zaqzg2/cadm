package com.cady.cadymarketing.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.ColorUtils
import com.cady.cadymarketing.domain.model.AppStyle

/** The hero / status colours that sit beside the Material scheme — read with `MaterialTheme.cady`. */
val LocalCadyColors = staticCompositionLocalOf { LightExtraColors }

val MaterialTheme.cady: CadyExtraColors
    @Composable
    @ReadOnlyComposable
    get() = LocalCadyColors.current

/**
 * @param darkTheme light or dark; the caller decides (the phone's setting, or the choice in Settings → المظهر).
 * @param brandColor the colour taken from the brand identity (set in the Identity screen, phase 2).
 *        Null — or the default brand red — uses the hand-tuned red schemes; any other colour goes
 *        through the HSL-tone derivation below, the same approach Kady uses for its colour presets.
 * @param style how the page, the cards and the bars are drawn (Settings → المظهر). The colours always
 *        come from the brand scheme, so every style works with every brand colour, in light and dark.
 */
@Composable
fun CadyMarketingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    brandColor: Color? = null,
    style: AppStyle = AppStyle.DEFAULT,
    content: @Composable () -> Unit,
) {
    val baseScheme = remember(brandColor, darkTheme) {
        if (brandColor == null || brandColor == CadyRed) {
            if (darkTheme) DarkColorScheme else LightColorScheme
        } else {
            deriveColorScheme(brandColor, darkTheme)
        }
    }
    val extraColors = remember(baseScheme, darkTheme) { buildExtraColors(baseScheme, darkTheme) }
    val look = remember(style, baseScheme, extraColors, darkTheme) {
        buildStyleTokens(style, baseScheme, extraColors, darkTheme)
    }

    // The app is Arabic-only regardless of the device's system language, so RTL is forced here
    // (same as Kady) rather than left to follow the system locale.
    CompositionLocalProvider(
        LocalLayoutDirection provides LayoutDirection.Rtl,
        LocalCadyColors provides extraColors,
        LocalCadyStyle provides look,
    ) {
        MaterialTheme(
            colorScheme = look.colorScheme,
            typography = look.typography,
            shapes = look.shapes,
        ) {
            // One page under the whole app. Every screen's own frame is see-through, so the style's
            // background (flat, glows, a wash) shows behind the top bar, the content and the bottom bar alike.
            Box(modifier = Modifier.fillMaxSize().styledBackdrop(look)) {
                content()
            }
        }
    }
}

/** Hero-panel colours follow the chosen brand colour; the status colours stay fixed. */
private fun buildExtraColors(scheme: ColorScheme, dark: Boolean): CadyExtraColors {
    val base = if (dark) DarkExtraColors else LightExtraColors
    return if (dark) {
        base.copy(
            heroStart = lerp(scheme.primaryContainer, Color.Black, 0.25f),
            heroEnd = lerp(scheme.primaryContainer, scheme.primary, 0.30f),
            onHero = scheme.onPrimaryContainer,
        )
    } else {
        base.copy(
            heroStart = lerp(scheme.primary, Color.Black, 0.28f),
            heroEnd = lerp(scheme.primary, Color.White, 0.08f),
            onHero = Color.White,
        )
    }
}

private fun deriveColorScheme(seed: Color, dark: Boolean): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.RGBToHSL(
        (seed.red * 255).toInt().coerceIn(0, 255),
        (seed.green * 255).toInt().coerceIn(0, 255),
        (seed.blue * 255).toInt().coerceIn(0, 255),
        hsl,
    )
    val hue = hsl[0]
    fun tone(lightness: Float): Color =
        Color(ColorUtils.HSLToColor(floatArrayOf(hue, hsl[1].coerceAtLeast(0.35f), lightness)))
    // Page and card surfaces: the seed's hue at very low saturation, so a blue brand gets cool greys
    // and a green brand gets soft green-greys.
    fun neutral(lightness: Float, saturation: Float = 0.07f): Color =
        Color(ColorUtils.HSLToColor(floatArrayOf(hue, saturation, lightness)))

    return if (dark) {
        darkColorScheme(
            primary = tone(0.75f),
            onPrimary = tone(0.15f),
            primaryContainer = tone(0.30f),
            onPrimaryContainer = tone(0.90f),
            secondary = tone(0.70f),
            onSecondary = tone(0.15f),
            secondaryContainer = neutral(0.26f, 0.14f),
            onSecondaryContainer = neutral(0.90f, 0.20f),
            tertiary = tone(0.65f),
            onTertiary = tone(0.15f),
            background = neutral(0.075f),
            onBackground = neutral(0.90f),
            surface = neutral(0.075f),
            onSurface = neutral(0.90f),
            surfaceVariant = neutral(0.24f),
            onSurfaceVariant = neutral(0.80f),
            outline = neutral(0.60f),
            outlineVariant = neutral(0.30f),
            surfaceDim = neutral(0.075f),
            surfaceBright = neutral(0.24f),
            surfaceContainerLowest = neutral(0.05f),
            surfaceContainerLow = neutral(0.10f),
            surfaceContainer = neutral(0.13f),
            surfaceContainerHigh = neutral(0.17f),
            surfaceContainerHighest = neutral(0.21f),
        )
    } else {
        lightColorScheme(
            primary = tone(0.40f),
            onPrimary = Color.White,
            primaryContainer = tone(0.90f),
            onPrimaryContainer = tone(0.15f),
            secondary = tone(0.45f),
            onSecondary = Color.White,
            secondaryContainer = neutral(0.90f, 0.20f),
            onSecondaryContainer = neutral(0.15f, 0.25f),
            tertiary = tone(0.35f),
            onTertiary = Color.White,
            background = neutral(0.965f),
            onBackground = neutral(0.10f),
            surface = neutral(0.965f),
            onSurface = neutral(0.10f),
            surfaceVariant = neutral(0.92f),
            onSurfaceVariant = neutral(0.32f),
            outline = neutral(0.50f),
            outlineVariant = neutral(0.87f),
            surfaceDim = neutral(0.86f),
            surfaceBright = Color.White,
            surfaceContainerLowest = Color.White,
            surfaceContainerLow = neutral(0.98f),
            surfaceContainer = neutral(0.945f),
            surfaceContainerHigh = neutral(0.92f),
            surfaceContainerHighest = neutral(0.90f),
        )
    }
}
