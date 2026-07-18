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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.collectLatest
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.R
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

private val LANGUAGE_OPTIONS @Composable get() = listOf(
    AslDropdownOption(stringResource(R.string.general_language_en), "en"),
    AslDropdownOption(stringResource(R.string.general_language_ar), "ar"),
)

@Composable
fun GeneralRoute(
    onBack: () -> Unit,
    viewModel: GeneralViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        viewModel.effect.collectLatest { effect ->
            when (effect) {
                GeneralEffect.RecreateForLocale -> (context as? android.app.Activity)?.recreate()
            }
        }
    }
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
            AslTopAppBar(title = stringResource(R.string.general_title), onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                val swatches = listOf(
                    AslThemeSwatch("emerald", stringResource(R.string.general_accent_emerald), listOf(Color(0xFF10B981))),
                    AslThemeSwatch("fjord", stringResource(R.string.general_accent_fjord), listOf(Color(0xFF3B82F6))),
                    AslThemeSwatch("amber", stringResource(R.string.general_accent_amber), listOf(Color(0xFFF59E0B))),
                )
                GeneralUiModeSection(uiState = uiState, interactionListener = interactionListener, colors = colors)
                AslThemeSwatchPicker(
                    label = stringResource(R.string.general_accent),
                    swatches = swatches,
                    value = uiState.accentId,
                    onValueChange = { interactionListener.onAccentChanged(it) },
                    modifier = Modifier.padding(top = 20.dp),
                )
                AslDropdown(
                    label = stringResource(R.string.general_language),
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
    Text(text = stringResource(R.string.general_ui_mode), style = MaterialTheme.typography.labelMedium, color = colors.textSecondary, modifier = Modifier.padding(bottom = 8.dp))
    AslSegmentedButton(
        options = listOf(
            AslSegmentedOption(stringResource(R.string.general_theme_light), "light", "sun"),
            AslSegmentedOption(stringResource(R.string.general_theme_dark), "dark", "moon"),
            AslSegmentedOption(stringResource(R.string.general_theme_system), "system", "monitor"),
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
            label = stringResource(R.string.general_auto_open_last),
            checked = uiState.autoOpenLastProject,
            onCheckedChange = { interactionListener.onToggleAutoOpenLastProject(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        AslSwitch(
            label = stringResource(R.string.general_snowfall),
            checked = uiState.snowfallEasterEgg,
            onCheckedChange = { interactionListener.onToggleSnowfallEasterEgg(it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    Text(
        text = stringResource(R.string.general_snowfall_hint),
        style = MaterialTheme.typography.bodySmall,
        color = colors.textTertiary,
        modifier = Modifier.padding(top = 8.dp, start = 4.dp),
    )
}
