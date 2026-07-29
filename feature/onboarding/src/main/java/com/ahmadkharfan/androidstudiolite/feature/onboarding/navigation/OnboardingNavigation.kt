package com.ahmadkharfan.androidstudiolite.feature.onboarding.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ahmadkharfan.androidstudiolite.feature.onboarding.complete.CompleteRoute
import com.ahmadkharfan.androidstudiolite.feature.onboarding.howitworks.HowItWorksRoute
import com.ahmadkharfan.androidstudiolite.feature.onboarding.permissions.PermissionsRoute
import com.ahmadkharfan.androidstudiolite.feature.onboarding.welcome.WelcomeRoute

/**
 * Registers onboarding's destinations. Leaving the flow is a caller decision, so [onFinished] is
 * supplied by the host rather than this feature knowing what comes next.
 */
fun NavGraphBuilder.onboardingGraph(
    navigateTo: (String) -> Unit,
    onFinished: () -> Unit,
) {
    composable(OnboardingRoutes.WELCOME) {
        WelcomeRoute(onGetStarted = { navigateTo(OnboardingRoutes.HOW_IT_WORKS) })
    }
    composable(OnboardingRoutes.HOW_IT_WORKS) {
        HowItWorksRoute(onContinue = { navigateTo(OnboardingRoutes.PERMISSIONS) })
    }
    composable(OnboardingRoutes.PERMISSIONS) {
        PermissionsRoute(onContinue = { navigateTo(OnboardingRoutes.COMPLETE) })
    }
    composable(OnboardingRoutes.COMPLETE) {
        CompleteRoute(onOpenHub = onFinished)
    }
}
