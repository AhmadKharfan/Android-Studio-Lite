package com.example.androidstudiolite.feature.settings.buildrun

interface BuildRunInteractionListener {
    fun onGradleJvmPathChanged(path: String)
    fun onToggleParallelTaskExecution(enabled: Boolean)
    fun onToggleBuildCache(enabled: Boolean)
    fun onToggleConfigurationCache(enabled: Boolean)
    fun onToggleLaunchAfterInstall(enabled: Boolean)
    fun onToggleInstallViaShizuku(enabled: Boolean)
}
