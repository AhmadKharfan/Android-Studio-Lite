package com.example.androidstudiolite.feature.settings.general.screen

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.example.androidstudiolite.core.designsystem.component.inputs.AslDropdown
import com.example.androidstudiolite.core.designsystem.component.inputs.AslDropdownOption
import com.example.androidstudiolite.core.designsystem.component.inputs.AslSegmentedButton
import com.example.androidstudiolite.core.designsystem.component.inputs.AslSegmentedOption
import com.example.androidstudiolite.core.designsystem.component.inputs.AslSwitch
import com.example.androidstudiolite.core.designsystem.component.ide.AslThemeSwatch
import com.example.androidstudiolite.core.designsystem.component.ide.AslThemeSwatchPicker
import com.example.androidstudiolite.core.designsystem.component.navigation.AslTopAppBar
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme
import com.example.androidstudiolite.domain.model.AppThemeMode
import com.example.androidstudiolite.feature.settings.general.interaction.GeneralInteraction
import com.example.androidstudiolite.feature.settings.general.uiState.GeneralUiState
import com.example.androidstudiolite.feature.settings.general.viewModel.GeneralViewModel

private val ACCENT_SWATCHES = listOf(
    AslThemeSwatch("emerald", "Nordic Emerald", listOf(Color(0xFF10B981))),
    AslThemeSwatch("fjord", "Fjord Blue", listOf(Color(0xFF3B82F6))),
    AslThemeSwatch("amber", "Amber", listOf(Color(0xFFF59E0B))),
)

private val LANGUAGE_OPTIONS = listOf(
    AslDropdownOption("English", "en"),
    AslDropdownOption("Deutsch", "de"),
    AslDropdownOption("日本語", "ja"),
)

@Composable
fun GeneralRoute(
    onBack: () -> Unit,
    viewModel: GeneralViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GeneralScreen(uiState = uiState, onInteraction = viewModel::onInteraction, onBack = onBack)
}

@Composable
private fun GeneralScreen(
    uiState: GeneralUiState,
    onInteraction: (GeneralInteraction) -> Unit,
    onBack: () -> Unit,
) {
    val colors = AslTheme.colors
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AslTopAppBar(title = "General", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                Text(text = "UI mode", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
                AslSegmentedButton(
                    options = listOf(
                        AslSegmentedOption("Light", "light", "sun"),
                        AslSegmentedOption("Dark", "dark", "moon"),
                        AslSegmentedOption("System", "system", "monitor"),
                    ),
                    value = uiState.themeMode.name.lowercase(),
                    onValueChange = { onInteraction(GeneralInteraction.ThemeModeChanged(it.uppercase().let(AppThemeMode::valueOf))) },
                    fullWidth = true,
                )
                AslThemeSwatchPicker(
                    label = "Accent",
                    swatches = ACCENT_SWATCHES,
                    value = uiState.accentId,
                    onValueChange = { onInteraction(GeneralInteraction.AccentChanged(it)) },
                    modifier = Modifier.padding(top = 20.dp),
                )
                AslDropdown(
                    label = "Language",
                    value = uiState.language,
                    onValueChange = { onInteraction(GeneralInteraction.LanguageChanged(it)) },
                    options = LANGUAGE_OPTIONS,
                    modifier = Modifier.padding(top = 20.dp),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp)
                        .background(colors.surface, AslShape.lg)
                        .border(1.dp, colors.borderDefault, AslShape.lg)
                        .padding(horizontal = 16.dp),
                ) {
                    AslSwitch(
                        label = "Auto-open last project",
                        checked = uiState.autoOpenLastProject,
                        onCheckedChange = { onInteraction(GeneralInteraction.ToggleAutoOpenLastProject(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AslSwitch(
                        label = "Snowfall in December",
                        checked = uiState.snowfallEasterEgg,
                        onCheckedChange = { onInteraction(GeneralInteraction.ToggleSnowfallEasterEgg(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Text(
                    text = "A quiet seasonal easter egg. Off by default.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp),
                )
            }
        }
    }
}
