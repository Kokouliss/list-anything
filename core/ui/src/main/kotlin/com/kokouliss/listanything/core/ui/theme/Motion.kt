package com.kokouliss.listanything.core.ui.theme

import android.animation.ValueAnimator
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.LongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember

/** How things move. The same figures as `motion` in `opendesigner/state.json`; change them there first. */
object Motion {
    /** A press, a tick. */
    const val SHORT = 150

    /** One screen or part giving way to the next. */
    const val LONG = 250

    /** A drawing that draws itself, a pen stroke. */
    const val REVEAL = 600

    /** One loop of the loader. */
    const val LOADER_LOOP = 2400

    /** One rise and fall of a floating bubble. Each bubble adds a little, so they never move as one. */
    const val FLOAT_PERIOD = 3600

    /** How far a floating bubble rises and falls, in dp. */
    const val FLOAT_REACH = 5f

    /** Fast at first, then settling. */
    val Enter = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

    /** What a pressed card or bubble shrinks to. */
    const val PRESS_SCALE = 0.97f

    /** A spring with a little give, for things that follow a finger. */
    fun <T> momentum(): FiniteAnimationSpec<T> = spring(dampingRatio = 0.8f, stiffness = 246.7f)
}

/**
 * One page or tab giving way to the next: the new one comes in a little from the side it lies on,
 * [forward] or back, while the old one fades. Without [slide], for a phone with animations off,
 * it only fades.
 */
fun pageChange(forward: Boolean, slide: Boolean = true): ContentTransform {
    val fade = fadeIn(tween(Motion.LONG)) togetherWith fadeOut(tween(Motion.SHORT))
    if (!slide) return fade
    return slideInHorizontally(tween(Motion.LONG, easing = Motion.Enter)) { if (forward) it / 8 else -it / 8 } + fade.targetContentEnter togetherWith fade.initialContentExit
}

/** True when the phone is set to remove animations. Motion that never ends stays still then. */
// shortcut: read once per screen, so a change in the phone's settings shows on the next screen opened.
@Composable
fun rememberReducedMotion(): Boolean = remember { !ValueAnimator.areAnimatorsEnabled() }

/** Milliseconds since its first frame, for motion that never ends. Stays 0 while [moving] is false. */
@Composable
fun rememberEndlessMillis(moving: Boolean): LongState {
    val millis = remember { mutableLongStateOf(0L) }
    // Counted from its own first frame, so a loader always starts at the start of its loop.
    if (moving) LaunchedEffect(Unit) {
        val start = withInfiniteAnimationFrameMillis { it }
        while (true) withInfiniteAnimationFrameMillis { millis.longValue = it - start }
    }
    return millis
}
