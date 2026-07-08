package com.example.androidstudiolite.feature.onboarding.setup.interaction

sealed interface SetupInteraction {
    data object StartSetup : SetupInteraction
}
