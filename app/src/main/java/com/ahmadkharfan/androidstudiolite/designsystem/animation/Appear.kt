package com.ahmadkharfan.androidstudiolite.designsystem.animation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion

/**
 * Fades + lifts [content] into place the first time it enters composition, offsetting the start by
 * [index] so a row/column of items cascades in rather than snapping. Purely presentational — the
 * item is laid out immediately; only its paint is animated.
 */
@Composable
fun AslStaggeredAppear(
    index: Int = 0,
    modifier: Modifier = Modifier,
    staggerMillis: Int = 45,
    content: @Composable () -> Unit,
) {
    val state = remember { MutableTransitionState(initialState = false).apply { targetState = true } }
    val delay = index * staggerMillis
    AnimatedVisibility(
        visibleState = state,
        modifier = modifier,
        enter = fadeIn(tween(AslMotion.slow, delayMillis = delay, easing = AslMotion.easeEnter)) +
            slideInVertically(
                animationSpec = tween(AslMotion.slow, delayMillis = delay, easing = AslMotion.easeEnter),
                initialOffsetY = { it / 5 },
            ),
    ) {
        content()
    }
}
