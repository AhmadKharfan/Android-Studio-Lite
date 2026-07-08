package com.example.androidstudiolite.core.designsystem.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing

/** tokens/motion.css — durations in ms and the three named easing curves. */
object AslMotion {
    const val instant: Int = 0
    const val fast: Int = 120
    const val normal: Int = 200
    const val slow: Int = 300

    val easeEnter: Easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)
    val easeExit: Easing = CubicBezierEasing(0.4f, 0f, 1f, 1f)
    val easeStandard: Easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
}
