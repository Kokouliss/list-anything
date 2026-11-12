package com.kokouliss.listanything.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.assertTrue
import org.junit.Test

/** The palette is chosen by hand, so this keeps a later change of one colour readable. */
class ColorContrastTest {

    /** The WCAG contrast ratio, from 1 (the same colour) to 21 (black on white). */
    private fun contrast(a: Color, b: Color): Float {
        val (light, dark) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (light + 0.05f) / (dark + 0.05f)
    }

    @Test
    fun textAndIconsKeepAtLeast4_5to1() {
        for (colors in listOf(LightColors, DarkColors)) {
            val pairs = with(colors) {
                listOf(
                    onPrimary to primary,
                    onPrimaryContainer to primaryContainer,
                    onSecondaryContainer to secondaryContainer,
                    onTertiaryContainer to tertiaryContainer,
                    onErrorContainer to errorContainer,
                    inverseOnSurface to inverseSurface,
                    onSurface to surface,
                    onSurface to surfaceContainerLow,
                    onSurface to surfaceContainerHighest,
                    onSurfaceVariant to surface,
                    onSurfaceVariant to surfaceContainerLow,
                    onSurfaceVariant to surfaceContainerHighest,
                    primary to surface,
                    primary to surfaceContainerLow,
                    error to surface,
                )
            }
            pairs.forEach { (text, paper) ->
                assertTrue(contrast(text, paper) >= 4.5f, "$text on $paper is ${contrast(text, paper)}")
            }
        }
    }

    @Test
    fun outlinesAndBubblesKeepAtLeast3to1() {
        for (colors in listOf(LightColors, DarkColors)) {
            assertTrue(contrast(colors.outline, colors.surface) >= 3f)
            // The margin line of a list.
            assertTrue(contrast(colors.tertiary, colors.surface) >= 3f)
        }
        LightBubbles.forEach { (fill, _) -> assertTrue(contrast(fill, LightColors.background) >= 3f, "$fill") }
        DarkBubbles.forEach { (fill, _) -> assertTrue(contrast(fill, DarkColors.background) >= 3f, "$fill") }
    }

    @Test
    fun textOverTheBackdropNotesKeepsAtLeast4_5to1() {
        for (colors in listOf(LightColors, DarkColors)) {
            for (paper in NotePapers) {
                val note = paper.copy(alpha = NOTES_ALPHA).compositeOver(colors.surface)
                for (text in listOf(colors.onSurface, colors.onSurfaceVariant, colors.primary, colors.error)) {
                    assertTrue(contrast(text, note) >= 4.5f, "$text on $note is ${contrast(text, note)}")
                }
            }
        }
    }

    @Test
    fun bubbleTextKeepsAtLeast4_5to1() {
        (LightBubbles + DarkBubbles).forEach { (fill, text) ->
            assertTrue(contrast(text, fill) >= 4.5f, "$text on $fill is ${contrast(text, fill)}")
        }
    }
}
