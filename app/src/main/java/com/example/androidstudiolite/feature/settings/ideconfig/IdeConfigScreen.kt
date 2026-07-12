package com.example.androidstudiolite.feature.settings.ideconfig
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.example.androidstudiolite.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.example.androidstudiolite.designsystem.component.content.AslListItem
import com.example.androidstudiolite.designsystem.component.feedback.AslBanner
import com.example.androidstudiolite.designsystem.component.feedback.AslBannerTone
import com.example.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.example.androidstudiolite.designsystem.component.feedback.AslStatus
import com.example.androidstudiolite.designsystem.component.feedback.AslStatusChip
import com.example.androidstudiolite.designsystem.component.inputs.AslChip
import com.example.androidstudiolite.designsystem.component.inputs.AslChipKind
import com.example.androidstudiolite.designsystem.component.inputs.AslChipStatus
import com.example.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.example.androidstudiolite.designsystem.theme.AslColorScheme
import com.example.androidstudiolite.designsystem.theme.AslMotion
import com.example.androidstudiolite.designsystem.theme.AslShape
import com.example.androidstudiolite.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.settings.ideconfig.IdeConfigInteractionListener
import com.example.androidstudiolite.feature.settings.ideconfig.IdeComponentUiModel
import com.example.androidstudiolite.feature.settings.ideconfig.IdeConfigUiState
import com.example.androidstudiolite.feature.settings.ideconfig.IdeConfigViewModel

@Composable
fun IdeConfigRoute(onBack: () -> Unit, viewModel: IdeConfigViewModel = koinViewModel()) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    IdeConfigScreen(uiState = uiState, interactionListener = viewModel, onBack = onBack)
}

@Composable
private fun IdeConfigScreen(
    uiState: IdeConfigUiState,
    interactionListener: IdeConfigInteractionListener,
    onBack: () -> Unit,
) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AslTopAppBar(
                title = "IDE Configurations",
                onBack = onBack,
                actions = { IdeConfigNetworkChip(uiState = uiState) },
            )
            IdeConfigContent(uiState = uiState, interactionListener = interactionListener, colors = colors)
        }
    }
}

@Composable
private fun IdeConfigNetworkChip(uiState: IdeConfigUiState) {
    AslChip(
        label = if (!uiState.networkAvailable) "Offline" else "Online",
        kind = AslChipKind.Status,
        status = if (!uiState.networkAvailable) AslChipStatus.Error else AslChipStatus.Success,
        icon = if (!uiState.networkAvailable) "wifi-off" else "wifi",
    )
}

@Composable
private fun IdeConfigContent(
    uiState: IdeConfigUiState,
    interactionListener: IdeConfigInteractionListener,
    colors: AslColorScheme,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IdeConfigOfflineBanner(uiState = uiState, interactionListener = interactionListener)
        if (uiState.unsupportedDevice) {
            AslBanner(
                tone = AslBannerTone.Warning,
                message = "This device's CPU architecture isn't supported yet — only arm64-v8a and armeabi-v7a devices can run the on-device build.",
            )
            return@Column
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface, AslShape.lg)
                .border(1.dp, colors.borderDefault, AslShape.lg)
                .padding(4.dp),
        ) {
            uiState.components.forEachIndexed { index, component ->
                ComponentCard(
                    component = component,
                    installDisabled = !uiState.networkAvailable,
                    divider = index != uiState.components.lastIndex,
                    onInstall = { interactionListener.onInstallComponent(component.id) },
                )
            }
        }
    }
}

@Composable
private fun IdeConfigOfflineBanner(
    uiState: IdeConfigUiState,
    interactionListener: IdeConfigInteractionListener,
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
            onAction = { interactionListener.onRetryConnection() },
        )
    }
}

@Composable
private fun ComponentCard(
    component: IdeComponentUiModel,
    installDisabled: Boolean,
    divider: Boolean,
    onInstall: () -> Unit,
) {
    Column {
        AslListItem(
            title = component.title,
            subtitle = if (installDisabled && component.status == IdeConfigComponentStatus.NotInstalled) {
                "${component.subtitle} · needs network"
            } else {
                component.subtitle
            },
            icon = component.icon,
            divider = false,
            trailing = {
                when (component.status) {
                    IdeConfigComponentStatus.Installed -> AslStatusChip(status = AslStatus.Success, label = "Ready")
                    IdeConfigComponentStatus.Downloading -> AslStatusChip(status = AslStatus.Building, label = "Downloading")
                    IdeConfigComponentStatus.Verifying -> AslStatusChip(status = AslStatus.Syncing, label = "Verifying")
                    IdeConfigComponentStatus.Extracting -> AslStatusChip(status = AslStatus.Building, label = "Extracting")
                    IdeConfigComponentStatus.Failed -> AslStatusChip(status = AslStatus.Failed, label = "Failed")
                    IdeConfigComponentStatus.NotInstalled -> AslButton(
                        label = "Install",
                        onClick = onInstall,
                        variant = AslButtonVariant.Secondary,
                        disabled = installDisabled,
                    )
                }
            },
        )
        if (component.status == IdeConfigComponentStatus.Downloading || component.status == IdeConfigComponentStatus.Extracting) {
            AslLinearProgress(
                value = if (component.status == IdeConfigComponentStatus.Downloading) component.progressPercent.toFloat() else null,
                label = if (component.status == IdeConfigComponentStatus.Downloading) "Downloading ${component.title}" else "Extracting ${component.title}",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (component.status == IdeConfigComponentStatus.Failed && component.errorMessage != null) {
            AslBanner(
                tone = AslBannerTone.Error,
                message = component.errorMessage,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            )
        }
        if (divider) {
            HorizontalDivider(color = AslTheme.colors.borderSubtle, thickness = 1.dp)
        }
    }
}
