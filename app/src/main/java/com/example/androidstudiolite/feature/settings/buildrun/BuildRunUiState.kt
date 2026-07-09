package com.example.androidstudiolite.feature.settings.buildrun
import androidx.compose.runtime.Immutable

@Immutable
data class BuildRunUiState(
    val gradleJvmPath: String = "",
    val parallelTaskExecution: Boolean = true,
    val buildCacheEnabled: Boolean = true,
    val configurationCacheEnabled: Boolean = false,
    val launchAfterInstall: Boolean = true,
    val installViaShizuku: Boolean = false,
)
