package com.example.androidstudiolite.feature.onboarding.statistics
sealed interface StatisticsInteraction {
    data class ToggleShareUsageStats(val enabled: Boolean) : StatisticsInteraction
}
