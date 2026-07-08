package com.example.androidstudiolite.feature.onboarding.statistics.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButtonSize
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButtonVariant
import com.example.androidstudiolite.core.designsystem.component.feedback.AslBanner
import com.example.androidstudiolite.core.designsystem.component.inputs.AslSwitch
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.onboarding.common.ONBOARDING_STEPS
import com.example.androidstudiolite.feature.onboarding.statistics.interaction.StatisticsInteraction
import com.example.androidstudiolite.feature.onboarding.statistics.uiState.StatisticsUiState
import com.example.androidstudiolite.feature.onboarding.statistics.viewModel.StatisticsViewModel
import com.example.androidstudiolite.core.designsystem.component.inputs.AslWizardStepper

@Composable
fun StatisticsRoute(
    onContinue: () -> Unit,
    onSkip: () -> Unit,
    viewModel: StatisticsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    StatisticsScreen(
        uiState = uiState,
        onInteraction = viewModel::onInteraction,
        onContinue = onContinue,
        onSkip = onSkip,
    )
}

@Composable
private fun StatisticsScreen(
    uiState: StatisticsUiState,
    onInteraction: (StatisticsInteraction) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            AslWizardStepper(steps = ONBOARDING_STEPS, current = 0)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(colors.surfaceContainerLow, AslShape.md),
                    contentAlignment = Alignment.Center,
                ) {
                    AslIcon(name = "chart-no-axes-column", size = 26.dp, tint = colors.textSecondary)
                }
                Column(modifier = Modifier.padding(top = 16.dp)) {
                    Text(text = "Help improve Android Studio Lite", style = MaterialTheme.typography.headlineMedium, color = colors.textPrimary)
                    Text(
                        text = "Share anonymous usage statistics — feature usage and build performance only.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp)
                        .background(colors.surface, AslShape.lg)
                        .border(1.dp, colors.borderDefault, AslShape.lg)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    AslSwitch(
                        label = "Share usage statistics",
                        checked = uiState.shareUsageStats,
                        onCheckedChange = { onInteraction(StatisticsInteraction.ToggleShareUsageStats(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Box(modifier = Modifier.padding(top = 16.dp)) {
                    AslBanner(tone = com.example.androidstudiolite.core.designsystem.component.feedback.AslBannerTone.Info, message = "No personal data, code, or file names are ever collected.")
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AslButton(label = "Continue", onClick = onContinue, size = AslButtonSize.Lg, fullWidth = true)
                AslButton(label = "Skip", onClick = onSkip, variant = AslButtonVariant.Tertiary, fullWidth = true)
            }
        }
    }
}
