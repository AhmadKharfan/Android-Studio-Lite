package com.example.androidstudiolite.feature.onboarding.setup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import com.example.androidstudiolite.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.designsystem.component.buttons.AslButtonSize
import com.example.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.example.androidstudiolite.designsystem.component.content.AslListItem
import com.example.androidstudiolite.designsystem.component.feedback.AslBanner
import com.example.androidstudiolite.designsystem.component.feedback.AslBannerTone
import com.example.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.example.androidstudiolite.designsystem.component.feedback.AslStatus
import com.example.androidstudiolite.designsystem.component.feedback.AslStatusChip
import com.example.androidstudiolite.designsystem.component.inputs.AslWizardStepper
import com.example.androidstudiolite.designsystem.theme.AslColorScheme
import com.example.androidstudiolite.designsystem.theme.AslShape
import com.example.androidstudiolite.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.onboarding.common.ONBOARDING_STEPS
import com.example.androidstudiolite.feature.onboarding.setup.InstallStatus
import com.example.androidstudiolite.feature.onboarding.setup.SetupInteractionListener
import com.example.androidstudiolite.feature.onboarding.setup.SetupUiState
import com.example.androidstudiolite.feature.onboarding.setup.SetupViewModel

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
            AslWizardStepper(steps = ONBOARDING_STEPS, current = 2, modifier = Modifier.padding(horizontal = 4.dp))
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
            text = "A JDK and the Android SDK are installed once, locally.",
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
        Column(
            modifier = Modifier
                .background(colors.surface, AslShape.lg)
                .border(1.dp, colors.borderDefault, AslShape.lg)
                .padding(4.dp),
        ) {
            AslListItem(
                title = "OpenJDK 17",
                subtitle = "jdk-17.0.11 · 289 MB",
                icon = "coffee",
                trailing = { AslStatusChip(status = AslStatus.Success, label = "Installed") },
            )
            AslListItem(
                title = "Android SDK 34",
                subtitle = "platform-34 + build-tools",
                icon = "smartphone",
                divider = false,
                trailing = {
                    when (uiState.sdkStatus) {
                        InstallStatus.Installed -> AslStatusChip(status = AslStatus.Success, label = "Installed")
                        InstallStatus.Installing -> AslStatusChip(status = AslStatus.Building, label = "Installing")
                        InstallStatus.Failed -> AslStatusChip(status = AslStatus.Failed, label = "Failed")
                        InstallStatus.Pending -> AslStatusChip(status = AslStatus.Indexing, label = "Queued")
                    }
                },
            )
            if (uiState.sdkStatus == InstallStatus.Installing) {
                AslLinearProgress(
                    value = uiState.sdkProgressPercent.toFloat(),
                    label = "Downloading platform-34",
                    detail = uiState.sdkDetail,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
        AslBanner(tone = AslBannerTone.Info, message = "Setup runs in the terminal — you can watch or hide it.")
    }
}

@Composable
private fun SetupActions(
    uiState: SetupUiState,
    interactionListener: SetupInteractionListener,
    onSkip: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        AslButton(
            label = "Continue setup",
            onClick = { interactionListener.onStartSetup() },
            size = AslButtonSize.Lg,
            fullWidth = true,
            icon = "terminal",
            loading = uiState.sdkStatus == InstallStatus.Installing,
        )
        AslButton(label = "I'll do this later", onClick = onSkip, variant = AslButtonVariant.Tertiary, fullWidth = true)
        AslBanner(tone = AslBannerTone.Warning, message = "Projects can't build until setup completes.")
    }
}
