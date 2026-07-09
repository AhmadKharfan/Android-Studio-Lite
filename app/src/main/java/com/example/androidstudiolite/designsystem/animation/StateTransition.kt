package com.example.androidstudiolite.designsystem.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.androidstudiolite.designsystem.theme.AslMotion

/**
 * Cross-fades between discrete UI states (e.g. loading ↔ empty ↔ content) on the shared motion
 * timing, so a screen eases from one state into the next instead of snapping. [content] receives the
 * value each fading slot was composed for, so an outgoing slot keeps rendering its own state while it
 * fades out (don't read external state that has already moved on — read the passed [T]).
 */
@Composable
fun <T> AslStateCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String = "AslStateCrossfade",
    content: @Composable (T) -> Unit,
) {
    Crossfade(
        targetState = targetState,
        modifier = modifier,
        animationSpec = AslMotion.standardSpec(),
        label = label,
    ) { state ->
        content(state)
    }
}

/**
 * Shared-axis (horizontal) swap for master ↔ detail navigation within a panel (e.g. a changes list —
 * ↔ a diff view): the incoming pane slides in from the direction of travel while the outgoing one
 * drifts the opposite way, both cross-fading.
 *
 * [contentKey] collapses [targetState] to the value that actually distinguishes panes, so unrelated
 * state churn (a keystroke in a text field) updates a pane in place instead of re-triggering the
 * slide. Each active pane retains the [targetState] it was composed with, so the outgoing pane still
 * renders its own (now-stale) data through the animation.
 */
@Composable
fun <T> AslSlideContent(
    targetState: T,
    modifier: Modifier = Modifier,
    contentKey: (T) -> Any? = { it as Any? },
    isForward: (initial: T, target: T) -> Boolean = { _, _ -> true },
    label: String = "AslSlideContent",
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        contentKey = contentKey,
        transitionSpec = {
            val dir = if (isForward(initialState, targetState)) 1 else -1
            (slideInHorizontally(AslMotion.offsetSpec()) { w -> dir * w / 4 } + fadeIn(AslMotion.enterSpec()))
                .togetherWith(
                    slideOutHorizontally(AslMotion.offsetSpec()) { w -> -dir * w / 4 } + fadeOut(AslMotion.exitSpec()),
                )
        },
        label = label,
    ) { state ->
        content(state)
    }
}
