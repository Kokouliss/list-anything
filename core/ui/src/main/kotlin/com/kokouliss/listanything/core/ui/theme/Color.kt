package com.kokouliss.listanything.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// A paper notepad in strong ink: manila paper under white cards, near-black ink, ballpoint blue for
// actions, pad yellow for highlights, the red of a margin line (tertiary) and the blue of ruled lines
// (outlineVariant). Chosen by hand. ColorContrastTest keeps every text pair at 4.5:1 or more.
internal val LightColors = lightColorScheme(
    primary = Color(0xFF173B9C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFCB2E),
    onPrimaryContainer = Color(0xFF231B00),
    secondary = Color(0xFF5B4E2E),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE9D9A9),
    onSecondaryContainer = Color(0xFF221A05),
    tertiary = Color(0xFFC23420),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD2C7),
    onTertiaryContainer = Color(0xFF3F0900),
    error = Color(0xFFB3261E),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF93000A),
    background = Color(0xFFF7F0DD),
    onBackground = Color(0xFF12151C),
    surface = Color(0xFFF7F0DD),
    onSurface = Color(0xFF12151C),
    surfaceVariant = Color(0xFFE6DCC0),
    onSurfaceVariant = Color(0xFF3A372F),
    outline = Color(0xFF5F5A4E),
    outlineVariant = Color(0xFF8FA8CE),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF2B2D33),
    inverseOnSurface = Color(0xFFF5F0E4),
    inversePrimary = Color(0xFFAFC6FF),
    surfaceDim = Color(0xFFD9CFB4),
    surfaceBright = Color(0xFFFFFBEF),
    // Cards and sheets are white, so they stand out from the paper under them.
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFFFFF),
    surfaceContainer = Color(0xFFEFE6CE),
    surfaceContainerHigh = Color(0xFFE9DFC4),
    surfaceContainerHighest = Color(0xFFE2D7B9),
    primaryFixed = Color(0xFFFFCB2E),
    primaryFixedDim = Color(0xFFE0AF14),
    onPrimaryFixed = Color(0xFF231B00),
    onPrimaryFixedVariant = Color(0xFF5A4700),
    secondaryFixed = Color(0xFFE9D9A9),
    secondaryFixedDim = Color(0xFFD9CBA4),
    onSecondaryFixed = Color(0xFF221A05),
    onSecondaryFixedVariant = Color(0xFF4A4027),
    tertiaryFixed = Color(0xFFFFD2C7),
    tertiaryFixedDim = Color(0xFFFF9A85),
    onTertiaryFixed = Color(0xFF3F0900),
    onTertiaryFixedVariant = Color(0xFF7A1F12),
)

// The same notepad at night: the ink of the AnyKind logos as paper, bright writing, and the pad
// yellow kept at full strength.
internal val DarkColors = darkColorScheme(
    primary = Color(0xFFAFC6FF),
    onPrimary = Color(0xFF06245F),
    primaryContainer = Color(0xFFFFD24A),
    onPrimaryContainer = Color(0xFF241C00),
    secondary = Color(0xFFD9CBA4),
    onSecondary = Color(0xFF3A2F12),
    secondaryContainer = Color(0xFF4A4027),
    onSecondaryContainer = Color(0xFFF4E6BF),
    tertiary = Color(0xFFFF9A85),
    onTertiary = Color(0xFF5C1206),
    tertiaryContainer = Color(0xFF7A1F12),
    onTertiaryContainer = Color(0xFFFFDAD2),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1014),
    onBackground = Color(0xFFF5F0E4),
    surface = Color(0xFF0E1014),
    onSurface = Color(0xFFF5F0E4),
    surfaceVariant = Color(0xFF3A3F4A),
    onSurfaceVariant = Color(0xFFCDC7B8),
    outline = Color(0xFFA19B8E),
    outlineVariant = Color(0xFF46577A),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFF5F0E4),
    inverseOnSurface = Color(0xFF2B2D33),
    inversePrimary = Color(0xFF173B9C),
    surfaceDim = Color(0xFF0E1014),
    surfaceBright = Color(0xFF383D48),
    surfaceContainerLowest = Color(0xFF090A0D),
    surfaceContainerLow = Color(0xFF1B1E25),
    surfaceContainer = Color(0xFF20242C),
    surfaceContainerHigh = Color(0xFF2A2F38),
    surfaceContainerHighest = Color(0xFF343A45),
    primaryFixed = Color(0xFFFFCB2E),
    primaryFixedDim = Color(0xFFE0AF14),
    onPrimaryFixed = Color(0xFF231B00),
    onPrimaryFixedVariant = Color(0xFF5A4700),
    secondaryFixed = Color(0xFFE9D9A9),
    secondaryFixedDim = Color(0xFFD9CBA4),
    onSecondaryFixed = Color(0xFF221A05),
    onSecondaryFixedVariant = Color(0xFF4A4027),
    tertiaryFixed = Color(0xFFFFD2C7),
    tertiaryFixedDim = Color(0xFFFF9A85),
    onTertiaryFixed = Color(0xFF3F0900),
    onTertiaryFixedVariant = Color(0xFF7A1F12),
)

// Fill and text colour of the space bubbles: the blue of the app and the teal, red, ochre and mauve
// of the AnyKind logo. Strong on paper, lighter with dark text at night.
internal val LightBubbles = listOf(
    Color(0xFF173B9C) to Color.White,
    Color(0xFF1F6F82) to Color.White,
    Color(0xFFB4472E) to Color.White,
    Color(0xFFAD7D22) to Color(0xFF12151C),
    Color(0xFF7A4B77) to Color.White,
)
internal val DarkBubbles = listOf(0xFF9DB8FF, 0xFF6FC4D6, 0xFFF08F78, 0xFFE6B559, 0xFFCFA0CC)
    .map { Color(it) to Color(0xFF0E1014) }

/** The margin red of a list at night, drawn at half strength so it does not glare. */
internal val NightMarginRed = Color(0xFFFF5A45)

@Composable
private fun isNight() = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** The fill and the text colour of a bubble. The same [seed] always gives the same pair. */
@Composable
fun bubbleColors(seed: Int): Pair<Color, Color> {
    val bubbles = if (isNight()) DarkBubbles else LightBubbles
    return bubbles[seed.mod(bubbles.size)]
}

/** The margin line of a list: a faint red on paper, fainter still at night so it does not glare. */
@Composable
fun marginLineColor(): Color =
    if (isNight()) NightMarginRed.copy(alpha = 0.25f) else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.4f)
