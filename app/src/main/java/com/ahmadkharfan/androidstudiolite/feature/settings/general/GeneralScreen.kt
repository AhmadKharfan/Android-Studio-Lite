package com.ahmadkharfan.androidstudiolite.feature.settings.general
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
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslDropdown
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslDropdownOption
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSegmentedButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSegmentedOption
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSwitch
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslThemeSwatch
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslThemeSwatchPicker
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.domain.model.AppThemeMode
import com.ahmadkharfan.androidstudiolite.feature.settings.general.GeneralInteractionListener
import com.ahmadkharfan.androidstudiolite.feature.settings.general.GeneralUiState
import com.ahmadkharfan.androidstudiolite.feature.settings.general.GeneralViewModel

private val ACCENT_SWATCHES = listOf(
    AslThemeSwatch("emerald", "Nordic Emerald", listOf(Color(0xFF10B981))),
    AslThemeSwatch("fjord", "Fjord Blue", listOf(Color(0xFF3B82F6))),
    AslThemeSwatch("amber", "Amber", listOf(Color(0xFFF59E0B))),
)

private val LANGUAGE_OPTIONS = listOf(
    AslDropdownOption("English", "en"),
    AslDropdownOption("العربية", "ar"),
)

@Composable
fun GeneralRoute(
    onBack: () -> Unit,
    viewModel: GeneralViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    GeneralScreen(uiState = uiState, interactionListener = viewModel, onBack = onBack)
}

@Composable
private fun GeneralScreen(
    uiState: GeneralUiState,
    interactionListener: GeneralInteractionListener,
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
                GeneralUiModeSection(uiState = uiState, interactionListener = interactionListener, colors = colors)
                AslThemeSwatchPicker(
                    label = "Accent",
                    swatches = ACCENT_SWATCHES,
                    value = uiState.accentId,
                    onValueChange = { interactionListener.onAccentChanged(it) },
                    modifier = Modifier.padding(top = 20.dp),
                )
                AslDropdown(
                    label = "Language",
                    value = uiState.language,
                    onValueChange = { interactionListener.onLanguageChanged(it) },
                    options = LANGUAGE_OPTIONS,
                    modifier = Modifier.padding(top = 20.dp),
                )
                GeneralTogglesSection(uiState = uiState, interactionListener = interactionListener, colors = colors)
            }
        }
    }
}

@Composable
private fun GeneralUiModeSection(
    uiState: GeneralUiState,
    interactionListener: GeneralInteractionListener,
    colors: AslColorScheme,
) {
    Text(text = "UI mode", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
    AslSegmentedButton(
        options = listOf(
            AslSegmentedOption("Light", "light", "sun"),
            AslSegmentedOption("Dark", "dark", "moon"),
            AslSegmentedOption("System", "system", "monitor"),
        ),
        value = uiState.themeMode.name.lowercase(),
        onValueChange = { interactionListener.onThemeModeChanged(it.uppercase().let(AppThemeMode::valueOf)) },
        fullWidth = true,
    )
}

@Composable
private fun GeneralTogglesSection(
    uiState: GeneralUiState,
    interactionListener: GeneralInteractionListener,
    colors: AslColorScheme,
) {
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
            onCheckedChange = { interactionListener.onToggleAutoOpenLastProject(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        AslSwitch(
            label = "Snowfall in December",
            checked = uiState.snowfallEasterEgg,
            onCheckedChange = { interactionListener.onToggleSnowfallEasterEgg(it) },
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
