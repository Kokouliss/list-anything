package com.kokouliss.listanything.core.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

// Headings in the phone's serif, like the printed head of a notepad. It has Greek letters too.
private val AppTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontFamily = FontFamily.Serif),
        headlineMedium = headlineMedium.copy(fontFamily = FontFamily.Serif),
        headlineSmall = headlineSmall.copy(fontFamily = FontFamily.Serif),
        titleLarge = titleLarge.copy(fontFamily = FontFamily.Serif),
        titleMedium = titleMedium.copy(fontFamily = FontFamily.Serif),
        titleSmall = titleSmall.copy(fontFamily = FontFamily.Serif),
    )
}

// Corners a little tighter than the Material default, closer to cut paper.
private val AppShapes = Shapes(
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** The notepad look. Always these colours: the wallpaper colours of Android 12 would replace the paper. */
@Composable
fun ListAnythingTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
