package com.kokouliss.listanything.core.ui.theme

import androidx.annotation.FontRes
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kokouliss.listanything.core.ui.R

/** One cut of a variable font file per weight the app writes in. [opticalSize] picks the drawing made for that size of letter. */
private fun family(@FontRes font: Int, opticalSize: Float? = null) = FontFamily(
    listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map { weight ->
        val axes = listOfNotNull(FontVariation.weight(weight.weight), opticalSize?.let { FontVariation.Setting("opsz", it) })
        Font(font, weight, variationSettings = FontVariation.Settings(*axes.toTypedArray()))
    },
)

// Both faces travel with the app, so a heading looks the same on every phone, and both have Greek.
// Literata is a book face: the printed head of a notepad. Its large cut is drawn for headings.
private val Headings = family(R.font.core_ui_literata, opticalSize = 28f)
private val SmallHeadings = family(R.font.core_ui_literata)
private val Writing = family(R.font.core_ui_inter)

// Inter is drawn with its own room between letters; Material's extra spacing is for Roboto.
private fun TextStyle.written() = copy(fontFamily = Writing, letterSpacing = 0.sp)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = Headings),
        displayMedium = displayMedium.copy(fontFamily = Headings),
        displaySmall = displaySmall.copy(fontFamily = Headings),
        headlineLarge = headlineLarge.copy(fontFamily = Headings),
        headlineMedium = headlineMedium.copy(fontFamily = Headings),
        headlineSmall = headlineSmall.copy(fontFamily = Headings),
        titleLarge = titleLarge.copy(fontFamily = Headings),
        titleMedium = titleMedium.copy(fontFamily = SmallHeadings),
        titleSmall = titleSmall.copy(fontFamily = SmallHeadings),
        bodyLarge = bodyLarge.written(),
        bodyMedium = bodyMedium.written(),
        bodySmall = bodySmall.written(),
        labelLarge = labelLarge.written(),
        labelMedium = labelMedium.written(),
        labelSmall = labelSmall.written(),
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
        // Material's own parts (buttons, switches, sheets) move on springs with a little give.
        motionScheme = MotionScheme.expressive(),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/** Figures of one width, for prices and totals that change while you look: the digits stay in place. */
fun TextStyle.tabular(): TextStyle = copy(fontFeatureSettings = "tnum")
