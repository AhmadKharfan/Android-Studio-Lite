package com.example.androidstudiolite.feature.settings.buildrun
sealed interface BuildRunInteraction {
    data class GradleJvmPathChanged(val path: String) : BuildRunInteraction
    data class ToggleParallelTaskExecution(val enabled: Boolean) : BuildRunInteraction
    data class ToggleBuildCache(val enabled: Boolean) : BuildRunInteraction
    data class ToggleConfigurationCache(val enabled: Boolean) : BuildRunInteraction
    data class ToggleLaunchAfterInstall(val enabled: Boolean) : BuildRunInteraction
    data class ToggleInstallViaShizuku(val enabled: Boolean) : BuildRunInteraction
}
