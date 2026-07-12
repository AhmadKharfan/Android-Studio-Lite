package com.ahmadkharfan.androidstudiolite.feature.settings.buildrun
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
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSwitch
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.hub.components.HubSectionHeader
import com.ahmadkharfan.androidstudiolite.feature.settings.buildrun.BuildRunInteractionListener
import com.ahmadkharfan.androidstudiolite.feature.settings.buildrun.BuildRunUiState
import com.ahmadkharfan.androidstudiolite.feature.settings.buildrun.BuildRunViewModel

@Composable
fun BuildRunSettingsRoute(
    onBack: () -> Unit,
    viewModel: BuildRunViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    BuildRunSettingsScreen(uiState = uiState, interactionListener = viewModel, onBack = onBack)
}

@Composable
private fun BuildRunSettingsScreen(
    uiState: BuildRunUiState,
    interactionListener: BuildRunInteractionListener,
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
                    onValueChange = { interactionListener.onGradleJvmPathChanged(it) },
                    label = "Gradle JVM",
                    trailingIcon = "folder-open",
                    helper = "OpenJDK 17 · managed by IDE setup",
                )
                BuildRunOptimizationSection(uiState = uiState, interactionListener = interactionListener, colors = colors)
                BuildRunAfterBuildSection(uiState = uiState, interactionListener = interactionListener, colors = colors)
            }
        }
    }
}

@Composable
private fun BuildRunOptimizationSection(
    uiState: BuildRunUiState,
    interactionListener: BuildRunInteractionListener,
    colors: AslColorScheme,
) {
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
            onCheckedChange = { interactionListener.onToggleParallelTaskExecution(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        AslSwitch(
            label = "Build cache",
            checked = uiState.buildCacheEnabled,
            onCheckedChange = { interactionListener.onToggleBuildCache(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        AslSwitch(
            label = "Configuration cache",
            checked = uiState.configurationCacheEnabled,
            onCheckedChange = { interactionListener.onToggleConfigurationCache(it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun BuildRunAfterBuildSection(
    uiState: BuildRunUiState,
    interactionListener: BuildRunInteractionListener,
    colors: AslColorScheme,
) {
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
            onCheckedChange = { interactionListener.onToggleLaunchAfterInstall(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        AslSwitch(
            label = "Install via Shizuku",
            checked = uiState.installViaShizuku,
            onCheckedChange = { interactionListener.onToggleInstallViaShizuku(it) },
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
