package com.kokouliss.listanything.core.ui.component

import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.EaseOutBounce
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
import androidx.compose.ui.graphics.drawscope.translate
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

/** How the three bullets of the logo come in while something loads. Its lines never move. */
enum class LoaderStyle {
    /** They drop from the top and land beside their line. */
    Fall,

    /** They grow where they belong, and each is ticked. */
    GrowAndCheck,
}

// One loop, in parts of 1: the bullets come one after the other, rest in place, then fade together.
private const val STEP = 0.11f
private const val ARRIVE = 0.25f
private const val TICK = 0.1f
private const val LEAVE_AT = 0.8f
private const val LEAVE = 0.12f

/** A moment of the loop with everything in place: what a phone with animations off shows. */
internal const val LOADER_AT_REST = 0.7f

/** How far bullet [index] has come at [t] of the loop: 0 not yet there, 1 in place. */
internal fun arrival(t: Float, index: Int): Float = ((t - index * STEP) / ARRIVE).coerceIn(0f, 1f)

/** How much of the tick on bullet [index] is drawn at [t]. The pen starts once the bullet is in place. */
internal fun ticked(t: Float, index: Int): Float = ((t - index * STEP - ARRIVE) / TICK).coerceIn(0f, 1f)

/** How far the bullets have faded at the end of the loop. */
internal fun leaving(t: Float): Float = ((t - LEAVE_AT) / LEAVE).coerceIn(0f, 1f)

/** The app logo on its tile, with its bullets arriving again and again. Decoration: the caller says "Loading". */
@Composable
fun AppLoader(modifier: Modifier = Modifier, style: LoaderStyle = LoaderStyle.GrowAndCheck) {
    val logo = ImageVector.vectorResource(R.drawable.core_ui_logo_list)
    // The art of the logo itself, so nothing is drawn a second time: three bullets, then the lines.
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
            parts.dropLast(1).forEachIndexed { index, (bullet, fill) ->
                val box = bullet.getBounds()
                val arrived = arrival(t, index)
                when (style) {
                    // From just above the tile, which hides it, down to its place.
                    LoaderStyle.Fall -> {
                        val height = box.bottom + group.translationY / group.scaleY
                        translate(top = -(1f - EaseOutBounce.transform(arrived)) * height) { drawPath(bullet, fill, alpha) }
                    }
                    LoaderStyle.GrowAndCheck -> {
                        scale(EaseOutBack.transform(arrived), box.center) { drawPath(bullet, fill, alpha) }
                        val drawn = ticked(t, index)
                        if (drawn == 0f) return@forEachIndexed
                        // The triangle is widest low down, so its tick sits lower and is smaller.
                        val low = index == 2
                        val centre = if (low) box.center + Offset(0f, box.height * 0.16f) else box.center
                        val unit = box.width / if (low) 6f else 4f
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
    }
}
