package com.example.androidstudiolite.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.example.androidstudiolite.core.designsystem.theme.AslMotion
import kotlin.math.roundToInt

/**
 * Material 3 shared-axis (X) screen transitions, driven by [AslMotion] timing. The incoming screen
 * slides in from the leading edge while the outgoing one drifts the opposite way, both cross-fading —
 * giving forward/back navigation a clear sense of spatial direction instead of a flat fade.
 */
private const val INCOMING_FRACTION = 0.30f
private const val OUTGOING_FRACTION = 0.12f

private fun offset(fraction: Float): (Int) -> Int = { fullWidth -> (fullWidth * fraction).roundToInt() }

fun AnimatedContentTransitionScope<NavBackStackEntry>.aslEnter(): EnterTransition =
    slideInHorizontally(AslMotion.offsetSpec(), offset(INCOMING_FRACTION)) + fadeIn(AslMotion.enterSpec())

fun AnimatedContentTransitionScope<NavBackStackEntry>.aslExit(): ExitTransition =
    slideOutHorizontally(AslMotion.offsetSpec()) { -(it * OUTGOING_FRACTION).roundToInt() } +
        fadeOut(AslMotion.exitSpec())

fun AnimatedContentTransitionScope<NavBackStackEntry>.aslPopEnter(): EnterTransition =
    slideInHorizontally(AslMotion.offsetSpec()) { -(it * OUTGOING_FRACTION).roundToInt() } +
        fadeIn(AslMotion.enterSpec())

fun AnimatedContentTransitionScope<NavBackStackEntry>.aslPopExit(): ExitTransition =
    slideOutHorizontally(AslMotion.offsetSpec(), offset(INCOMING_FRACTION)) + fadeOut(AslMotion.exitSpec())
