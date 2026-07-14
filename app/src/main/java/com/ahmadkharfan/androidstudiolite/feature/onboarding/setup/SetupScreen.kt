package com.ahmadkharfan.androidstudiolite.feature.onboarding.setup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonSize
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslBanner
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslBannerTone
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslStatus
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslStatusChip
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslWizardStepper
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.onboarding.common.ONBOARDING_STEPS
import com.ahmadkharfan.androidstudiolite.feature.onboarding.setup.SetupInteractionListener
import com.ahmadkharfan.androidstudiolite.feature.onboarding.setup.SetupUiState
import com.ahmadkharfan.androidstudiolite.feature.onboarding.setup.SetupViewModel

@Composable
fun SetupRoute(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    viewModel: SetupViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    SetupScreen(
        uiState = uiState,
        interactionListener = viewModel,
        onContinue = onContinue,
        onSkip = onSkip,
    )
}

@Composable
private fun SetupScreen(
    uiState: SetupUiState,
    interactionListener: SetupInteractionListener,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = AslTheme.colors

    LaunchedEffect(uiState.setupComplete) {
        if (uiState.setupComplete) onContinue()
    }

    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            AslWizardStepper(steps = ONBOARDING_STEPS, current = 1, modifier = Modifier.padding(horizontal = 4.dp))
            SetupHeader(colors = colors)
            SetupInstallSection(uiState = uiState, colors = colors, modifier = Modifier.weight(1f))
            SetupActions(uiState = uiState, interactionListener = interactionListener, onSkip = onSkip)
        }
    }
}

@Composable
private fun SetupHeader(colors: AslColorScheme) {
    Column(modifier = Modifier.padding(horizontal = 4.dp, vertical = 18.dp)) {
        Text(text = "IDE environment", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
        Text(
            text = "Builds run in the cloud, so there's no toolchain to download — your environment is ready to go.",
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun SetupInstallSection(
    uiState: SetupUiState,
    colors: AslColorScheme,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (uiState.unsupportedDevice) {
            AslBanner(
                tone = AslBannerTone.Warning,
                message = "This device's CPU architecture isn't supported yet — only arm64-v8a and armeabi-v7a devices can run the on-device build.",
            )
            return@Column
        }

        Column(
            modifier = Modifier
                .background(colors.surface, AslShape.lg)
                .border(1.dp, colors.borderDefault, AslShape.lg)
                .padding(4.dp),
        ) {
            uiState.components.forEachIndexed { index, component ->
                SetupComponentRow(component = component, divider = index != uiState.components.lastIndex)
            }
            if (uiState.components.isEmpty()) {
                AslListItem(title = "Fetching environment details…", icon = "loader", divider = false)
            }
        }

        val failure = uiState.components.firstOrNull { it.status == SetupComponentStatus.Failed }
        if (failure != null) {
            AslBanner(tone = AslBannerTone.Error, message = failure.errorMessage ?: "Setup failed. Tap retry to try again.")
        } else {
            AslBanner(tone = AslBannerTone.Info, message = "Nothing to install — builds run on the cloud build service. You can continue.")
        }
    }
}

@Composable
private fun SetupComponentRow(component: SetupComponentUiModel, divider: Boolean) {
    Column {
        AslListItem(
            title = component.displayName,
            subtitle = "${component.version} · ${component.detail}",
            icon = component.icon,
            divider = false,
            trailing = {
                when (component.status) {
                    SetupComponentStatus.Installed -> AslStatusChip(status = AslStatus.Success, label = "Installed")
                    SetupComponentStatus.Downloading -> AslStatusChip(status = AslStatus.Building, label = "Downloading")
                    SetupComponentStatus.Verifying -> AslStatusChip(status = AslStatus.Syncing, label = "Verifying")
                    SetupComponentStatus.Extracting -> AslStatusChip(status = AslStatus.Building, label = "Extracting")
                    SetupComponentStatus.Failed -> AslStatusChip(status = AslStatus.Failed, label = "Failed")
                    SetupComponentStatus.NotInstalled -> AslStatusChip(status = AslStatus.Indexing, label = "Queued")
                }
            },
        )
        if (component.status == SetupComponentStatus.Downloading || component.status == SetupComponentStatus.Extracting) {
            AslLinearProgress(
                value = if (component.status == SetupComponentStatus.Downloading) component.progressPercent.toFloat() else null,
                label = if (component.status == SetupComponentStatus.Downloading) "Downloading ${component.displayName}" else "Extracting ${component.displayName}",
                detail = component.detail,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (divider) {
            HorizontalDivider(color = AslTheme.colors.borderSubtle, thickness = 1.dp)
        }
    }
}

@Composable
private fun SetupActions(
    uiState: SetupUiState,
    interactionListener: SetupInteractionListener,
    onSkip: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        val hasFailure = uiState.components.any { it.status == SetupComponentStatus.Failed }
        AslButton(
            label = if (hasFailure) "Retry setup" else "Continue setup",
            onClick = { interactionListener.onStartSetup() },
            size = AslButtonSize.Lg,
            fullWidth = true,
            icon = "terminal",
            loading = uiState.isInstalling,
            disabled = uiState.unsupportedDevice,
        )
        AslButton(label = "I'll do this later", onClick = onSkip, variant = AslButtonVariant.Tertiary, fullWidth = true)
    }
}
