package com.ahmadkharfan.androidstudiolite.feature.onboarding

import androidx.compose.runtime.Composable
import com.ahmadkharfan.androidstudiolite.feature.onboarding.api.OnboardingFeatureApi
import com.ahmadkharfan.androidstudiolite.feature.onboarding.complete.CompleteRoute
import com.ahmadkharfan.androidstudiolite.feature.onboarding.howitworks.HowItWorksRoute
import com.ahmadkharfan.androidstudiolite.feature.onboarding.permissions.PermissionsRoute
import com.ahmadkharfan.androidstudiolite.feature.onboarding.welcome.WelcomeRoute

internal class OnboardingFeatureApiImpl : OnboardingFeatureApi {

    @Composable
    override fun WelcomeEntry(onStart: () -> Unit) = WelcomeRoute(onGetStarted = onStart)

    @Composable
    override fun HowItWorksEntry(onContinue: () -> Unit) = HowItWorksRoute(onContinue = onContinue)

    @Composable
    override fun PermissionsEntry(onContinue: () -> Unit) = PermissionsRoute(onContinue = onContinue)

    @Composable
    override fun CompleteEntry(onOpenHub: () -> Unit) = CompleteRoute(onOpenHub = onOpenHub)
}
