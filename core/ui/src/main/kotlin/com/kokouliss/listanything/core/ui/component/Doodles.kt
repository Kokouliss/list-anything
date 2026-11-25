package com.kokouliss.listanything.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp
import com.kokouliss.listanything.core.ui.theme.Motion
import com.kokouliss.listanything.core.ui.theme.rememberReducedMotion

/**
 * A small pen drawing for a page with nothing on it yet. Drawn on a square of 96: [ink] is the pen,
 * [accent] the one stroke in the ink blue of the app.
 */
enum class Doodle(internal val ink: String, internal val accent: String) {
    Basket(
        "M30 44C30 16 66 16 66 44M16 44H80M22 44L28 78H68L74 44M38 54V68M48 54V68M58 54V68",
        "M52 40C52 30 58 24 68 22C70 32 64 40 56 42",
    ),
    Pin(
        "M48 84C48 84 24 58 24 40A24 24 0 1 1 72 40C72 58 48 84 48 84Z",
        "M39 40a9 9 0 1 0 18 0a9 9 0 1 0 -18 0",
    ),
    Gift(
        "M22 46H74V80H22ZM16 32H80V46H16Z",
        "M48 32V80M48 32C40 16 24 20 30 28C33 32 42 32 48 32C54 32 63 32 66 28C72 20 56 16 48 32",
    ),
    Pencil(
        "M62 16L78 32L36 74L16 80L22 60ZM54 24L70 40M22 60L36 74",
        "M46 86C54 78 58 92 66 84C71 80 75 88 82 84",
    ),
}

private const val DOODLE_SIZE = 96f

/** Longer than any one stroke of a doodle, in the units of its square. */
private const val LONGEST_STROKE = 400f

/** Decoration: the title under it says what the page is. */
@Composable
fun DoodleImage(doodle: Doodle, modifier: Modifier = Modifier) {
    val ink = MaterialTheme.colorScheme.onSurface
    val accent = MaterialTheme.colorScheme.primary
    val (inkPath, accentPath) = remember(doodle) {
        PathParser().parsePathString(doodle.ink).toPath() to PathParser().parsePathString(doodle.accent).toPath()
    }
    // The pen draws it once, when the page opens. With animations off on the phone it is there at once.
    val drawn = remember(doodle) { Animatable(0f) }
    val still = rememberReducedMotion()
    LaunchedEffect(doodle) { if (still) drawn.snapTo(1f) else drawn.animateTo(1f, tween(Motion.REVEAL)) }
    Canvas(modifier.size(DOODLE_SIZE.dp)) {
        scale(size.minDimension / DOODLE_SIZE, pivot = Offset.Zero) {
            // A dash as long as the longest stroke, slid along: every stroke shows as far as the pen has got.
            val unfinished = PathEffect.dashPathEffect(floatArrayOf(LONGEST_STROKE, LONGEST_STROKE), LONGEST_STROKE * (1f - drawn.value))
            val pen = Stroke(3f, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = unfinished.takeIf { drawn.value < 1f })
            drawPath(inkPath, ink, style = pen)
            drawPath(accentPath, accent, style = pen)
        }
    }
}
