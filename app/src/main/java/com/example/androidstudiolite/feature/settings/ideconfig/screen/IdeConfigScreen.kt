package com.example.androidstudiolite.feature.settings.ideconfig.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButtonVariant
import com.example.androidstudiolite.core.designsystem.component.content.AslListItem
import com.example.androidstudiolite.core.designsystem.component.feedback.AslBanner
import com.example.androidstudiolite.core.designsystem.component.feedback.AslBannerTone
import com.example.androidstudiolite.core.designsystem.component.feedback.AslStatus
import com.example.androidstudiolite.core.designsystem.component.feedback.AslStatusChip
import com.example.androidstudiolite.core.designsystem.component.inputs.AslChip
import com.example.androidstudiolite.core.designsystem.component.inputs.AslChipKind
import com.example.androidstudiolite.core.designsystem.component.inputs.AslChipStatus
import com.example.androidstudiolite.core.designsystem.component.inputs.AslSwitch
import com.example.androidstudiolite.core.designsystem.component.navigation.AslTopAppBar
import com.example.androidstudiolite.core.designsystem.theme.AslMotion
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme
import com.example.androidstudiolite.domain.model.IdeComponentStatus
import com.example.androidstudiolite.feature.settings.ideconfig.interaction.IdeConfigInteraction
import com.example.androidstudiolite.feature.settings.ideconfig.uiState.IdeComponentUiModel
import com.example.androidstudiolite.feature.settings.ideconfig.uiState.IdeConfigUiState
import com.example.androidstudiolite.feature.settings.ideconfig.viewModel.IdeConfigViewModel

@Composable
fun IdeConfigRoute(onBack: () -> Unit, viewModel: IdeConfigViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    IdeConfigScreen(uiState = uiState, onInteraction = viewModel::onInteraction, onBack = onBack)
}

@Composable
private fun IdeConfigScreen(
    uiState: IdeConfigUiState,
    onInteraction: (IdeConfigInteraction) -> Unit,
    onBack: () -> Unit,
) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AslTopAppBar(
                title = "IDE Configurations",
                onBack = onBack,
                actions = {
                    AslChip(
                        label = if (!uiState.networkAvailable || uiState.offlineMode) "Offline" else "Online",
                        kind = AslChipKind.Status,
                        status = when {
                            !uiState.networkAvailable -> AslChipStatus.Error
                            uiState.offlineMode -> AslChipStatus.Neutral
                            else -> AslChipStatus.Success
                        },
                        icon = if (!uiState.networkAvailable || uiState.offlineMode) "wifi-off" else "wifi",
                    )
                },
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AnimatedVisibility(
                    visible = !uiState.networkAvailable,
                    enter = expandVertically(AslMotion.enterSpec()) + fadeIn(AslMotion.enterSpec()),
                    exit = shrinkVertically(AslMotion.exitSpec()) + fadeOut(AslMotion.exitSpec()),
                ) {
                    AslBanner(
                        tone = AslBannerTone.Warning,
                        message = "No internet connection — component installs unavailable.",
                        actionLabel = "Retry",
                        onAction = { onInteraction(IdeConfigInteraction.RetryConnection) },
                    )
                }
                uiState.components.forEach { component ->
                    ComponentCard(
                        component = component,
                        installDisabled = !uiState.networkAvailable,
                        onInstall = { onInteraction(IdeConfigInteraction.InstallComponent(component.id)) },
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface, AslShape.lg)
                        .border(1.dp, colors.borderDefault, AslShape.lg)
                        .padding(horizontal = 4.dp),
                ) {
                    AslSwitch(
                        checked = uiState.offlineMode,
                        onCheckedChange = { onInteraction(IdeConfigInteraction.ToggleOfflineMode(it)) },
                        label = "Offline mode",
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                }
                Text(
                    text = "Offline mode builds from cached dependencies only.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ComponentCard(component: IdeComponentUiModel, installDisabled: Boolean, onInstall: () -> Unit) {
    val colors = AslTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg),
    ) {
        AslListItem(
            title = component.title,
            subtitle = if (installDisabled && component.status == IdeComponentStatus.NotInstalled) "${component.subtitle} · needs network" else component.subtitle,
            icon = component.icon,
            divider = false,
            trailing = {
                when (component.status) {
                    IdeComponentStatus.Ready -> AslStatusChip(status = AslStatus.Success, label = "Ready")
                    IdeComponentStatus.Installing -> AslStatusChip(status = AslStatus.Syncing, label = "Installing")
                    IdeComponentStatus.NotInstalled -> AslButton(
                        label = "Install",
                        onClick = onInstall,
                        variant = AslButtonVariant.Secondary,
                        disabled = installDisabled,
                    )
                }
            },
        )
    }
}
