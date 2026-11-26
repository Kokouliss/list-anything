package com.kokouliss.listanything.core.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.kokouliss.listanything.core.ui.theme.Motion
import com.kokouliss.listanything.core.ui.theme.bubbleColors
import com.kokouliss.listanything.core.ui.theme.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

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
    /** A pad with its rings, and the first lines waiting to be written. */
    Pad(
        "M24 24H72V84H24ZM34 16V30M48 16V30M62 16V30",
        "M34 46H62M34 58H62M34 70H50",
    ),
    /** Spaces, as the bubbles they are drawn as. */
    Bubbles(
        "M14 44a22 22 0 1 0 44 0a22 22 0 1 0 -44 0M53 68a15 15 0 1 0 30 0a15 15 0 1 0 -30 0",
        "M59 24a9 9 0 1 0 18 0a9 9 0 1 0 -18 0",
    ),
    Clock(
        "M20 48a28 28 0 1 0 56 0a28 28 0 1 0 -56 0",
        "M48 30V48L60 56",
    ),
    /** A magnifying glass that found nothing. */
    Glass(
        "M18 40a22 22 0 1 0 44 0a22 22 0 1 0 -44 0M56 56L80 80",
        "M32 40H48",
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

/** How many dots a finished list throws. */
private const val INK_DOTS = 18

/**
 * A burst of ink dots for something rare and earned, like the last item of a list checked. Play it
 * by changing [play]; 0 draws nothing. Decoration: say the news in words as well. With animations
 * off on the phone it draws nothing.
 */
@Composable
fun InkBurst(play: Int, modifier: Modifier = Modifier) {
    val inks = List(5) { bubbleColors(it).first }
    val flight = remember { Animatable(1f) }
    val still = rememberReducedMotion()
    LaunchedEffect(play) {
        if (play == 0 || still) return@LaunchedEffect
        flight.snapTo(0f)
        flight.animateTo(1f, tween(Motion.REVEAL, easing = LinearEasing))
    }
    Canvas(modifier) {
        val t = flight.value
        if (t >= 1f) return@Canvas
        repeat(INK_DOTS) { dot ->
            // Every dot its own way out: round the circle, the odd ones further and bigger.
            val angle = (dot * 360f / INK_DOTS + play * 37f) * (PI / 180f).toFloat()
            // Fast out of the middle, then slowing; the ink stays strong until it is nearly there.
            val reach = size.minDimension * (if (dot % 2 == 0) 0.28f else 0.4f) * Motion.Enter.transform(t)
            drawCircle(
                color = inks[dot % inks.size],
                radius = (if (dot % 2 == 0) 4 else 6).dp.toPx(),
                center = center + Offset(cos(angle) * reach, sin(angle) * reach),
                alpha = 1f - t * t,
            )
        }
    }
}

/** How deep and how far apart the bites of [TornPaper] are, in dp. The same figures as `component.sheet.tear-*` in `opendesigner/state.json`. */
private const val TEAR_BITE = 3f
private const val TEAR_PITCH = 12f

/** A sheet torn off the pad: its top edge keeps the half holes of the perforation. */
val TornPaper: Shape = object : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val bite = TEAR_BITE * density.density
        // A whole number of holes, so the edge ends the way it starts.
        val holes = (size.width / (TEAR_PITCH * density.density)).roundToInt().coerceAtLeast(1)
        val pitch = size.width / holes
        return Outline.Generic(
            Path().apply {
                moveTo(0f, 0f)
                repeat(holes) { hole ->
                    val x = pitch * (hole + 0.5f)
                    arcTo(Rect(x - bite, -bite, x + bite, bite), 180f, -180f, forceMoveTo = false)
                }
                lineTo(size.width, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            },
        )
    }
}
