package com.example.androidstudiolite.feature.onboarding.setup
sealed interface SetupInteraction {
    data object StartSetup : SetupInteraction
}
