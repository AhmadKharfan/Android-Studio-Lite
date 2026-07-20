package com.ahmadkharfan.androidstudiolite.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.unit.IntOffset

object AslMotion {
    const val instant: Int = 0
    const val fast: Int = 120
    const val normal: Int = 200
    const val slow: Int = 300

    val easeEnter: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val easeExit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    val easeStandard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)

    fun <T> enterSpec(durationMillis: Int = normal): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = easeEnter)

    fun <T> exitSpec(durationMillis: Int = fast): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = easeExit)

    fun <T> standardSpec(durationMillis: Int = normal): FiniteAnimationSpec<T> =
        tween(durationMillis = durationMillis, easing = easeStandard)

    fun pressScaleSpec(): FiniteAnimationSpec<Float> =
        spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)

    fun <T> emphasizedSpec(): FiniteAnimationSpec<T> =
        spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessMediumLow)

    fun offsetSpec(durationMillis: Int = normal, easing: Easing = easeStandard): FiniteAnimationSpec<IntOffset> =
        tween(durationMillis = durationMillis, easing = easing)
}
