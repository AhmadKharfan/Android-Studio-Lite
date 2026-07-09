package com.example.androidstudiolite.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

/**
 * tokens/motion.css — durations (ms) and the three named easing curves, plus ready-made Compose
 * specs so every screen and component animates on the same timing language instead of ad-hoc tweens.
 */
object AslMotion {
    const val instant: Int = 0
    const val fast: Int = 120
    const val normal: Int = 200
    const val slow: Int = 300

    val easeEnter: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val easeExit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    val easeStandard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    /** Content entering the screen — decelerate. */
    fun <T> enterSpec(durationMillis: Int = normal): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = easeEnter)

    /** Content leaving the screen — accelerate. */
    fun <T> exitSpec(durationMillis: Int = fast): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = easeExit)

    /** Symmetric in-place change (color, size, alpha of a persistent element). */
    fun <T> standardSpec(durationMillis: Int = normal): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = easeStandard)

    /** Springy spec for tactile press/scale feedback — no overshoot, medium-low stiffness. */
    fun pressScaleSpec(): FiniteAnimationSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    /** Bouncy spec for elements that appear/transform (FAB, badges) — subtle overshoot. */
    fun <T> emphasizedSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

    /** Offset spec used by slide-in/out transitions. */
    fun offsetSpec(durationMillis: Int = normal, easing: Easing = easeStandard): FiniteAnimationSpec<IntOffset> =
        tween(durationMillis = durationMillis, easing = easing)
}
