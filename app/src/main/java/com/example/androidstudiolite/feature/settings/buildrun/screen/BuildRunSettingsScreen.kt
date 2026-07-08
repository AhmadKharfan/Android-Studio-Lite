package com.example.androidstudiolite.feature.settings.buildrun.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.example.androidstudiolite.core.designsystem.component.inputs.AslSwitch
import com.example.androidstudiolite.core.designsystem.component.inputs.AslTextField
import com.example.androidstudiolite.core.designsystem.component.navigation.AslTopAppBar
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.hub.components.HubSectionHeader
import com.example.androidstudiolite.feature.settings.buildrun.interaction.BuildRunInteraction
import com.example.androidstudiolite.feature.settings.buildrun.uiState.BuildRunUiState
import com.example.androidstudiolite.feature.settings.buildrun.viewModel.BuildRunViewModel

@Composable
fun BuildRunSettingsRoute(
    onBack: () -> Unit,
    viewModel: BuildRunViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    BuildRunSettingsScreen(uiState = uiState, onInteraction = viewModel::onInteraction, onBack = onBack)
}

@Composable
private fun BuildRunSettingsScreen(
    uiState: BuildRunUiState,
    onInteraction: (BuildRunInteraction) -> Unit,
    onBack: () -> Unit,
) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AslTopAppBar(title = "Build & Run", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                AslTextField(
                    value = uiState.gradleJvmPath,
                    onValueChange = { onInteraction(BuildRunInteraction.GradleJvmPathChanged(it)) },
                    label = "Gradle JVM",
                    trailingIcon = "folder-open",
                    helper = "OpenJDK 17 · managed by IDE setup",
                )
                HubSectionHeader("Optimization")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, AslShape.lg)
                        .border(1.dp, colors.borderDefault, AslShape.lg)
                        .padding(horizontal = 16.dp),
                ) {
                    AslSwitch(
                        label = "Parallel task execution",
                        checked = uiState.parallelTaskExecution,
                        onCheckedChange = { onInteraction(BuildRunInteraction.ToggleParallelTaskExecution(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AslSwitch(
                        label = "Build cache",
                        checked = uiState.buildCacheEnabled,
                        onCheckedChange = { onInteraction(BuildRunInteraction.ToggleBuildCache(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AslSwitch(
                        label = "Configuration cache",
                        checked = uiState.configurationCacheEnabled,
                        onCheckedChange = { onInteraction(BuildRunInteraction.ToggleConfigurationCache(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                HubSectionHeader("After build")
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, AslShape.lg)
                        .border(1.dp, colors.borderDefault, AslShape.lg)
                        .padding(horizontal = 16.dp),
                ) {
                    AslSwitch(
                        label = "Launch app after install",
                        checked = uiState.launchAfterInstall,
                        onCheckedChange = { onInteraction(BuildRunInteraction.ToggleLaunchAfterInstall(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AslSwitch(
                        label = "Install via Shizuku",
                        checked = uiState.installViaShizuku,
                        onCheckedChange = { onInteraction(BuildRunInteraction.ToggleInstallViaShizuku(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = "Shizuku installs APKs silently without the system prompt. Requires the Shizuku service.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                )
            }
        }
    }
}
