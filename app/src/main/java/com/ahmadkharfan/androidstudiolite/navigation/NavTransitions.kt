package com.ahmadkharfan.androidstudiolite.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslMotion
import kotlin.math.roundToInt

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
