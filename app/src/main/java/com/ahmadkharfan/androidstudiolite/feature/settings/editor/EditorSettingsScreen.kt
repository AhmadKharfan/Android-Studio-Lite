package com.ahmadkharfan.androidstudiolite.feature.settings.editor
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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.layout.aslImePadding
import com.ahmadkharfan.androidstudiolite.feature.editor.view.EditorPalette
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslThemeSwatch
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslThemeSwatchPicker
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSegmentedButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSegmentedOption
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSlider
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSwitch
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslCode
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.hub.components.HubSectionHeader
import com.ahmadkharfan.androidstudiolite.feature.settings.editor.EditorSettingsInteractionListener
import com.ahmadkharfan.androidstudiolite.feature.settings.editor.EditorSettingsUiState
import com.ahmadkharfan.androidstudiolite.feature.settings.editor.EditorSettingsViewModel

private val COLOR_SCHEME_SWATCHES = listOf(
    AslThemeSwatch("darcula", "Darcula", listOf(Color(0xFF1E1E1E), Color(0xFFCC7832), Color(0xFF6A8759))),
    AslThemeSwatch("hc", "High Contrast", listOf(Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF34D399))),
    AslThemeSwatch("light", "GitHub Light", listOf(Color(0xFFFFFFFF), Color(0xFFCF222E), Color(0xFF0A3069))),
)

@Composable
fun EditorSettingsRoute(
    onBack: () -> Unit,
    viewModel: EditorSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    EditorSettingsScreen(uiState = uiState, interactionListener = viewModel, onBack = onBack)
}

@Composable
private fun EditorSettingsScreen(
    uiState: EditorSettingsUiState,
    interactionListener: EditorSettingsInteractionListener,
    onBack: () -> Unit,
) {
    val colors = AslTheme.colors
    val previewPalette = EditorPalette.forScheme(uiState.colorSchemeId)
    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            AslTopAppBar(title = "Editor", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .aslImePadding()
                    .padding(16.dp),
            ) {
                EditorFontFamilySection(uiState = uiState, interactionListener = interactionListener, colors = colors)
                EditorFontSizeSlider(
                    uiState = uiState,
                    interactionListener = interactionListener,
                    previewCanvas = Color(previewPalette.canvas),
                    previewText = Color(previewPalette.defaultText),
                )
                AslThemeSwatchPicker(
                    label = "Color scheme",
                    swatches = COLOR_SCHEME_SWATCHES,
                    value = uiState.colorSchemeId,
                    onValueChange = { interactionListener.onColorSchemeChanged(it) },
                    modifier = Modifier.padding(top = 18.dp),
                )
                if (!EditorPalette.isDarkScheme(uiState.colorSchemeId)) {
                    Text(
                        text = "Light scheme — editor stays bright even in dark UI mode.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                EditorTabSizeSection(uiState = uiState, interactionListener = interactionListener, colors = colors)
                EditorBehaviorSection(uiState = uiState, interactionListener = interactionListener, colors = colors)
            }
        }
    }
}

@Composable
private fun EditorFontFamilySection(
    uiState: EditorSettingsUiState,
    interactionListener: EditorSettingsInteractionListener,
    colors: AslColorScheme,
) {
    Text(
        text = "Font family",
        style = MaterialTheme.typography.labelMedium,
        color = colors.textSecondary,
        modifier = Modifier.padding(bottom = 8.dp),
    )
    AslSegmentedButton(
        options = listOf(
            AslSegmentedOption("JetBrains Mono", "jetbrains"),
            AslSegmentedOption("System mono", "monospace"),
        ),
        value = uiState.fontFamilyId,
        onValueChange = { interactionListener.onFontFamilyChanged(it) },
        fullWidth = true,
    )
}

@Composable
private fun EditorFontSizeSlider(
    uiState: EditorSettingsUiState,
    interactionListener: EditorSettingsInteractionListener,
    previewCanvas: Color,
    previewText: Color,
) {
    val colors = AslTheme.colors
    Column(modifier = Modifier.padding(top = 18.dp)) {
        AslSlider(
            value = uiState.fontSize.toFloat(),
            onValueChange = { interactionListener.onFontSizeChanged(it.toInt()) },
            label = "Font size",
            valueRange = 10f..24f,
            unit = "sp",
        )
        Text(
            text = "val preview = \"Aa 0O 1lI\"",
            style = AslCode.codeBody.copy(fontSize = uiState.fontSize.sp),
            color = previewText,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .background(previewCanvas, AslShape.sm)
                .border(1.dp, colors.borderSubtle, AslShape.sm)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EditorTabSizeSection(
    uiState: EditorSettingsUiState,
    interactionListener: EditorSettingsInteractionListener,
    colors: AslColorScheme,
) {
    Text(
        text = "Tab size",
        style = MaterialTheme.typography.labelMedium,
        color = colors.textSecondary,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp),
    )
    AslSegmentedButton(
        options = listOf(
            AslSegmentedOption("2 spaces", "2"),
            AslSegmentedOption("4 spaces", "4"),
        ),
        value = uiState.tabSize.toString(),
        onValueChange = { interactionListener.onTabSizeChanged(it.toInt()) },
        fullWidth = true,
    )
}

@Composable
private fun EditorBehaviorSection(
    uiState: EditorSettingsUiState,
    interactionListener: EditorSettingsInteractionListener,
    colors: AslColorScheme,
) {
    HubSectionHeader("Behavior")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg)
            .padding(horizontal = 16.dp),
    ) {
        AslSwitch(
            label = "Auto-save",
            checked = uiState.autoSave,
            onCheckedChange = { interactionListener.onToggleAutoSave(it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
