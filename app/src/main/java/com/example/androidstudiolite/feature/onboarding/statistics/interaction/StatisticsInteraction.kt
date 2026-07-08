package com.example.androidstudiolite.feature.onboarding.statistics.interaction

sealed interface StatisticsInteraction {
    data class ToggleShareUsageStats(val enabled: Boolean) : StatisticsInteraction
}
