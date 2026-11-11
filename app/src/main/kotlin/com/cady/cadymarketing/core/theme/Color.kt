package com.cady.cadymarketing.core.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Brand red — identical to Kady's seed colour (also the launcher icon's colour).
val CadyRed = Color(0xFFC62828)
val CadyRedContainerLight = Color(0xFFFFE3E0)
val CadyRedContainerDark = Color(0xFF8F1D22)

/**
 * Same hand-tuned schemes as Kady: clean white cards on a warm off-white page, the brand colour kept
 * for the one thing that should stand out per screen (the main button, the selected tab).
 */
val LightColorScheme = lightColorScheme(
    primary = CadyRed,
    onPrimary = Color.White,
    primaryContainer = CadyRedContainerLight,
    onPrimaryContainer = Color(0xFF410004),
    inversePrimary = Color(0xFFFFB4AB),
    secondary = Color(0xFF6E5C58),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E3DF),
    onSecondaryContainer = Color(0xFF2A1815),
    tertiary = Color(0xFF7A5900),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFDEA3),
    onTertiaryContainer = Color(0xFF261A00),
    background = Color(0xFFF8F5F4),
    onBackground = Color(0xFF1F1A19),
    surface = Color(0xFFF8F5F4),
    onSurface = Color(0xFF1F1A19),
    surfaceVariant = Color(0xFFF1E7E5),
    onSurfaceVariant = Color(0xFF5B4F4D),
    surfaceTint = CadyRed,
    inverseSurface = Color(0xFF352F2E),
    inverseOnSurface = Color(0xFFFBEEEB),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF8A7C79),
    outlineVariant = Color(0xFFE4D9D6),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFE3DAD8),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFCF8F7),
    surfaceContainer = Color(0xFFF4EEEC),
    surfaceContainerHigh = Color(0xFFEEE7E5),
    surfaceContainerHighest = Color(0xFFE8E0DE),
)

val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF9E92),
    onPrimary = Color(0xFF5C0008),
    primaryContainer = CadyRedContainerDark,
    onPrimaryContainer = Color(0xFFFFDAD5),
    inversePrimary = CadyRed,
    secondary = Color(0xFFE7BDB2),
    onSecondary = Color(0xFF442A22),
    secondaryContainer = Color(0xFF4A3530),
    onSecondaryContainer = Color(0xFFFFDAD1),
    tertiary = Color(0xFFEBC166),
    onTertiary = Color(0xFF402D00),
    tertiaryContainer = Color(0xFF5C4200),
    onTertiaryContainer = Color(0xFFFFDEA3),
    background = Color(0xFF141110),
    onBackground = Color(0xFFEDE0DD),
    surface = Color(0xFF141110),
    onSurface = Color(0xFFEDE0DD),
    surfaceVariant = Color(0xFF3E3533),
    onSurfaceVariant = Color(0xFFD4C3BF),
    surfaceTint = Color(0xFFFF9E92),
    inverseSurface = Color(0xFFEDE0DD),
    inverseOnSurface = Color(0xFF352F2E),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF9C8D8A),
    outlineVariant = Color(0xFF4F4543),
    scrim = Color.Black,
    surfaceBright = Color(0xFF3B3534),
    surfaceDim = Color(0xFF141110),
    surfaceContainerLowest = Color(0xFF0F0C0B),
    surfaceContainerLow = Color(0xFF1C1817),
    surfaceContainer = Color(0xFF211C1B),
    surfaceContainerHigh = Color(0xFF2B2524),
    surfaceContainerHighest = Color(0xFF362F2E),
)

/**
 * Meaning-carrying colours that sit beside the Material scheme and do not follow the brand colour:
 * success is always green, warning amber, info blue. The hero pair paints the one gradient panel
 * per screen and follows the brand colour (see buildExtraColors in Theme.kt).
 */
@Immutable
data class CadyExtraColors(
    val success: Color,
    val warning: Color,
    val info: Color,
    val heroStart: Color,
    val heroEnd: Color,
    val onHero: Color,
)

val LightExtraColors = CadyExtraColors(
    success = Color(0xFF1B8A5A),
    warning = Color(0xFFC77700),
    info = Color(0xFF1565C0),
    heroStart = Color(0xFF8E1B1F),
    heroEnd = Color(0xFFD93636),
    onHero = Color.White,
)

val DarkExtraColors = CadyExtraColors(
    success = Color(0xFF5FD3A0),
    warning = Color(0xFFFFB347),
    info = Color(0xFF8AB8FF),
    heroStart = Color(0xFF5A1216),
    heroEnd = Color(0xFF8F1D22),
    onHero = Color(0xFFFFDAD5),
)
