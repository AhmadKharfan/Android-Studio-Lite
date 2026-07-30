package com.ahmadkharfan.androidstudiolite.feature.onboarding.api

import androidx.compose.runtime.Composable

/**
 * The onboarding flow as the host sees it: one composable per destination, and callbacks for
 * whatever comes next. The host owns the back stack, so this feature never learns another
 * feature's routes.
 *
 * No default argument values on these members — a `@Composable` interface member with defaults
 * makes the Compose compiler emit a defaults bridge that does not match an implementation compiled
 * in a different module, which fails at runtime with `AbstractMethodError`.
 */
public interface OnboardingFeatureApi {

    @Composable
    public fun WelcomeEntry(onStart: () -> Unit)

    @Composable
    public fun HowItWorksEntry(onContinue: () -> Unit)

    @Composable
    public fun PermissionsEntry(onContinue: () -> Unit)

    @Composable
    public fun CompleteEntry(onOpenHub: () -> Unit)
}
