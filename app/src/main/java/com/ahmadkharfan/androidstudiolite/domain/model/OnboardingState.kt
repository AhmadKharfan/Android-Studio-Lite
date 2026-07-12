package com.ahmadkharfan.androidstudiolite.domain.model

data class OnboardingPermission(
    val id: String,
    val title: String,
    val reason: String,
    val granted: Boolean,
    val optional: Boolean = false,
)

data class OnboardingState(
    val permissions: List<OnboardingPermission> = emptyList(),
    val setupComplete: Boolean = false,
    val onboardingComplete: Boolean = false,
)
