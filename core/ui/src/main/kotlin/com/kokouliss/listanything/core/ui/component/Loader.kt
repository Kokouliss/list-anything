package com.kokouliss.listanything.core.ui.component

import androidx.compose.animation.core.EaseOutBack
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.res.vectorResource
import com.kokouliss.listanything.core.ui.R
import com.kokouliss.listanything.core.ui.theme.Motion
import com.kokouliss.listanything.core.ui.theme.rememberEndlessMillis
import com.kokouliss.listanything.core.ui.theme.rememberReducedMotion

// One loop, in parts of 1: the six pieces come one after the other, rest in place, then fade together.
private const val STEP = 0.09f
private const val ARRIVE = 0.22f
private const val TICK = 0.08f
private const val LEAVE_AT = 0.82f
private const val LEAVE = 0.12f

/** The pieces of the logo, in the order they are drawn. */
internal const val PIECES = 6

/** A moment of the loop with everything in place: what a phone with animations off shows. */
internal const val LOADER_AT_REST = 0.78f

// How big each piece's tick is, in parts of the piece's width: pill, circle, square, diamond, plus, triangle.
private val TICK_SIZE = floatArrayOf(0.24f, 0.3f, 0.32f, 0.22f, 0.26f, 0.24f)

/** How far piece [index] has come at [t] of the loop: 0 not yet there, 1 in place. */
internal fun arrival(t: Float, index: Int): Float = ((t - index * STEP) / ARRIVE).coerceIn(0f, 1f)

/** How much of the tick on piece [index] is drawn at [t]. The pen starts once the piece is in place. */
internal fun ticked(t: Float, index: Int): Float = ((t - index * STEP - ARRIVE) / TICK).coerceIn(0f, 1f)

/** How far the pieces have faded at the end of the loop. */
internal fun leaving(t: Float): Float = ((t - LEAVE_AT) / LEAVE).coerceIn(0f, 1f)

/**
 * The app logo on its tile while something loads. Its lines never move; its six pieces grow where
 * they belong, one after the other, each gets a tick, and they fade to come again. The loop starts
 * from its beginning each time the loader appears. Decoration: the caller says "Loading".
 */
@Composable
fun AppLoader(modifier: Modifier = Modifier) {
    val logo = ImageVector.vectorResource(R.drawable.core_ui_logo_list)
    // The art of the logo itself, so nothing is drawn a second time: six pieces, then the lines.
    val group = logo.root.first() as VectorGroup
    val parts = remember(logo) {
        group.map { it as VectorPath }.map { PathParser().addPathNodes(it.pathData).toPath() to checkNotNull(it.fill) }
    }
    val still = rememberReducedMotion()
    val millis = rememberEndlessMillis(moving = !still)
    Canvas(modifier.clip(LogoTileShape).background(LogoTile)) {
        val t = if (still) LOADER_AT_REST else millis.longValue % Motion.LOADER_LOOP / Motion.LOADER_LOOP.toFloat()
        withTransform({
            scale(size.width / logo.viewportWidth, size.height / logo.viewportHeight, Offset.Zero)
            translate(group.translationX, group.translationY)
            scale(group.scaleX, group.scaleY, Offset.Zero)
        }) {
            val (lines, white) = parts.last()
            drawPath(lines, white)
            val alpha = 1f - leaving(t)
            parts.dropLast(1).forEachIndexed { index, (piece, fill) ->
                val box = piece.getBounds()
                scale(EaseOutBack.transform(arrival(t, index)), box.center) { drawPath(piece, fill, alpha) }
                val drawn = ticked(t, index)
                if (drawn == 0f) return@forEachIndexed
                // The triangle, last, is widest low down, so its tick sits lower.
                val centre = if (index == PIECES - 1) box.center + Offset(0f, box.height * 0.17f) else box.center
                val unit = box.width * TICK_SIZE[index]
                val from = centre + Offset(-unit, unit * 0.1f)
                val turn = centre + Offset(-unit * 0.3f, unit * 0.8f)
                val to = centre + Offset(unit * 1.1f, -unit * 0.8f)
                // The short stroke down, then the long one up.
                val down = (drawn / 0.35f).coerceAtMost(1f)
                val up = ((drawn - 0.35f) / 0.65f).coerceAtLeast(0f)
                val pen = unit * 0.55f
                drawLine(white, from, lerp(from, turn, down), pen, StrokeCap.Round, alpha = alpha)
                if (up > 0f) drawLine(white, turn, lerp(turn, to, up), pen, StrokeCap.Round, alpha = alpha)
            }
        }
    }
}
