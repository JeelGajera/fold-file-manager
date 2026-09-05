package com.jeelgajera.fold.core.design.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

/** Durations and easings from `tokens.json`. */
object FoldMotion {
    const val INSTANT = 0
    const val FAST = 200
    const val BASE = 260
    const val EMPHASIZED = 320
    const val SLIDE = 380
    const val INDICATOR = 420

    /** The one easing curve in the app. cubic-bezier(0.22, 1, 0.36, 1). */
    val Standard: Easing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    val Linear: Easing = LinearEasing

    /**
     * Reduced motion replaces every transform transition with a 120ms linear
     * opacity crossfade, stops the dock scaling, freezes glyph previews on a
     * single frame, and stops the search caret and every pulse loop.
     */
    const val REDUCED_CROSSFADE = 120
}

/**
 * True when the user has asked the system to remove animations.
 *
 * Read from `Settings.Global.ANIMATOR_DURATION_SCALE` by [ProvideFoldReducedMotion]
 * in the app module; components read it here so no component has to touch
 * Settings itself.
 */
val LocalFoldReducedMotion = staticCompositionLocalOf { false }

/**
 * A tween that honours reduced motion: the requested duration normally, the
 * 120ms linear crossfade when animations are switched off.
 */
@Composable
@ReadOnlyComposable
fun <T> foldTween(
    durationMillis: Int = FoldMotion.BASE,
    easing: Easing = FoldMotion.Standard,
): FiniteAnimationSpec<T> = if (LocalFoldReducedMotion.current) {
    tween(FoldMotion.REDUCED_CROSSFADE, easing = FoldMotion.Linear)
} else {
    tween(durationMillis, easing = easing)
}
