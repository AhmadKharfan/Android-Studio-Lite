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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.designsystem.editor.EditorPalette
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.layout.aslImePadding
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
                EditorFontFamilySection(
                    fontFamilyId = uiState.fontFamilyId,
                    onFontFamilyChanged = interactionListener::onFontFamilyChanged,
                    colors = colors,
                )
                EditorFontSizeSlider(
                    fontSize = uiState.fontSize,
                    colorSchemeId = uiState.colorSchemeId,
                    onFontSizeChanged = interactionListener::onFontSizeChanged,
                )
                EditorColorSchemeSection(
                    colorSchemeId = uiState.colorSchemeId,
                    onColorSchemeChanged = interactionListener::onColorSchemeChanged,
                    colors = colors,
                )
                EditorTabSizeSection(
                    tabSize = uiState.tabSize,
                    onTabSizeChanged = interactionListener::onTabSizeChanged,
                    colors = colors,
                )
                EditorBehaviorSection(
                    autoSave = uiState.autoSave,
                    onToggleAutoSave = interactionListener::onToggleAutoSave,
                    colors = colors,
                )
            }
        }
    }
}

@Composable
private fun EditorFontFamilySection(
    fontFamilyId: String,
    onFontFamilyChanged: (String) -> Unit,
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
        value = fontFamilyId,
        onValueChange = onFontFamilyChanged,
        fullWidth = true,
    )
}

@Composable
private fun EditorFontSizeSlider(
    fontSize: Int,
    colorSchemeId: String,
    onFontSizeChanged: (Int) -> Unit,
) {
    val colors = AslTheme.colors
    val previewPalette = remember(colorSchemeId) { EditorPalette.forScheme(colorSchemeId) }
    var localFontSize by remember { mutableIntStateOf(fontSize) }
    var dragging by remember { mutableStateOf(false) }
    val commitFontSize by rememberUpdatedState(onFontSizeChanged)

    LaunchedEffect(fontSize) {
        if (!dragging) localFontSize = fontSize
    }

    Column(modifier = Modifier.padding(top = 18.dp)) {
        AslSlider(
            value = localFontSize.toFloat(),
            onValueChange = {
                dragging = true
                localFontSize = it.toInt()
            },
            onValueChangeFinished = {
                dragging = false
                commitFontSize(localFontSize)
            },
            label = "Font size",
            valueRange = 10f..24f,
            unit = "sp",
        )
        Text(
            text = "val preview = \"Aa 0O 1lI\"",
            style = AslCode.codeBody.copy(fontSize = localFontSize.sp),
            color = Color(previewPalette.defaultText),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .background(Color(previewPalette.canvas), AslShape.sm)
                .border(1.dp, colors.borderSubtle, AslShape.sm)
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun EditorColorSchemeSection(
    colorSchemeId: String,
    onColorSchemeChanged: (String) -> Unit,
    colors: AslColorScheme,
) {
    AslThemeSwatchPicker(
        label = "Color scheme",
        swatches = COLOR_SCHEME_SWATCHES,
        value = colorSchemeId,
        onValueChange = onColorSchemeChanged,
        modifier = Modifier.padding(top = 18.dp),
    )
    if (!EditorPalette.isDarkScheme(colorSchemeId)) {
        Text(
            text = "Light scheme — editor stays bright even in dark UI mode.",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textTertiary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun EditorTabSizeSection(
    tabSize: Int,
    onTabSizeChanged: (Int) -> Unit,
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
        value = tabSize.toString(),
        onValueChange = { onTabSizeChanged(it.toInt()) },
        fullWidth = true,
    )
}

@Composable
private fun EditorBehaviorSection(
    autoSave: Boolean,
    onToggleAutoSave: (Boolean) -> Unit,
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
            checked = autoSave,
            onCheckedChange = onToggleAutoSave,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
