package com.ahmadkharfan.androidstudiolite.designsystem.animation

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion

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

@Composable
fun <T> AslToolRailContent(
    targetState: T,
    indexOf: (T) -> Int,
    modifier: Modifier = Modifier,
    label: String = "AslToolRailContent",
    content: @Composable (T) -> Unit,
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            val from = indexOf(initialState)
            val to = indexOf(targetState)
            val dir = if (to >= from) 1 else -1
            (slideInVertically(AslMotion.offsetSpec(AslMotion.fast)) { h -> dir * h / 5 } +
                fadeIn(AslMotion.enterSpec(AslMotion.fast)))
                .togetherWith(
                    slideOutVertically(AslMotion.exitSpec(AslMotion.fast)) { h -> -dir * h / 5 } +
                        fadeOut(AslMotion.exitSpec(AslMotion.fast)),
                )
        },
        label = label,
    ) { state ->
        content(state)
    }
}
