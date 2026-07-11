package com.example.androidstudiolite.feature.settings.developer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import com.example.androidstudiolite.designsystem.component.content.AslListItem
import com.example.androidstudiolite.designsystem.component.inputs.AslSwitch
import com.example.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.example.androidstudiolite.designsystem.icon.AslIcon
import com.example.androidstudiolite.designsystem.theme.AslShape
import com.example.androidstudiolite.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.settings.developer.DeveloperOptionsEffect
import com.example.androidstudiolite.feature.settings.developer.DeveloperOptionsInteractionListener
import com.example.androidstudiolite.feature.settings.developer.DeveloperOptionsUiState
import com.example.androidstudiolite.feature.settings.developer.DeveloperOptionsViewModel

private data class DeveloperRow(val title: String, val subtitle: String, val icon: String, val onClick: () -> Unit)

@Composable
fun DeveloperOptionsRoute(
    onBack: () -> Unit,
    onOpenUiDesigner: () -> Unit,
    onSimulateCrash: () -> Unit,
    onSimulateAcsMissing: () -> Unit,
    onSimulateUnsupportedDevice: () -> Unit,
    onSimulateSdCardInstall: () -> Unit,
    onSimulateSecondaryUser: () -> Unit,
    viewModel: DeveloperOptionsViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                DeveloperOptionsEffect.NavigateBack -> onBack()
                DeveloperOptionsEffect.NavigateToUiDesigner -> onOpenUiDesigner()
                DeveloperOptionsEffect.SimulateCrash -> onSimulateCrash()
                DeveloperOptionsEffect.SimulateAcsMissing -> onSimulateAcsMissing()
                DeveloperOptionsEffect.SimulateUnsupportedDevice -> onSimulateUnsupportedDevice()
                DeveloperOptionsEffect.SimulateSdCardInstall -> onSimulateSdCardInstall()
                DeveloperOptionsEffect.SimulateSecondaryUser -> onSimulateSecondaryUser()
            }
        }
    }

    DeveloperOptionsScreen(
        uiState = uiState,
        interactionListener = viewModel,
    )
}

@Composable
private fun DeveloperOptionsScreen(
    uiState: DeveloperOptionsUiState,
    interactionListener: DeveloperOptionsInteractionListener,
) {
    val colors = AslTheme.colors
    val rows = listOf(
        DeveloperRow("UI Designer preview", "Palette · canvas · properties", "layout", interactionListener::onOpenUiDesigner),
        DeveloperRow("Simulate crash", "Trigger the crash-recovery screen", "life-buoy", interactionListener::onSimulateCrash),
        DeveloperRow("Simulate ACS components missing", "Trigger the blocking reinstall dialog", "octagon-alert", interactionListener::onSimulateAcsMissing),
        DeveloperRow("Simulate unsupported device", "Trigger the blocking device-compat error", "smartphone", interactionListener::onSimulateUnsupportedDevice),
        DeveloperRow("Simulate SD card install", "Trigger the move-to-internal-storage error", "hard-drive", interactionListener::onSimulateSdCardInstall),
        DeveloperRow("Simulate secondary user", "Trigger the primary-user-required error", "users", interactionListener::onSimulateSecondaryUser),
    )
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AslTopAppBar(title = "Developer options", onBack = interactionListener::onBack)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, AslShape.lg)
                        .border(1.dp, colors.borderDefault, AslShape.lg),
                ) {
                    rows.forEachIndexed { index, row ->
                        AslListItem(
                            title = row.title,
                            subtitle = row.subtitle,
                            icon = row.icon,
                            divider = true,
                            trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
                            onClick = row.onClick,
                        )
                    }
                    AslSwitch(
                        checked = uiState.simulateOfflineNetwork,
                        onCheckedChange = { interactionListener.onToggleSimulateOffline(it) },
                        label = "Simulate offline network",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}
