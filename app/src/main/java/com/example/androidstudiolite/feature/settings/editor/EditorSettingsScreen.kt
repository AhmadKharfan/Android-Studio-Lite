package com.example.androidstudiolite.feature.settings.editor
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
import com.example.androidstudiolite.designsystem.theme.AslColorScheme
import org.koin.androidx.compose.koinViewModel
import com.example.androidstudiolite.designsystem.component.content.AslListItem
import com.example.androidstudiolite.designsystem.component.ide.AslThemeSwatch
import com.example.androidstudiolite.designsystem.component.ide.AslThemeSwatchPicker
import com.example.androidstudiolite.designsystem.component.inputs.AslSegmentedButton
import com.example.androidstudiolite.designsystem.component.inputs.AslSegmentedOption
import com.example.androidstudiolite.designsystem.component.inputs.AslSlider
import com.example.androidstudiolite.designsystem.component.inputs.AslSwitch
import com.example.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.example.androidstudiolite.designsystem.theme.AslCode
import com.example.androidstudiolite.designsystem.theme.AslShape
import com.example.androidstudiolite.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.hub.components.HubSectionHeader
import com.example.androidstudiolite.feature.settings.editor.EditorSettingsInteractionListener
import com.example.androidstudiolite.feature.settings.editor.EditorSettingsUiState
import com.example.androidstudiolite.feature.settings.editor.EditorSettingsViewModel

private val COLOR_SCHEME_SWATCHES = listOf(
    AslThemeSwatch("darcula", "Darcula", listOf(Color(0xFF1E1E1E), Color(0xFFCC7832), Color(0xFF6A8759))),
    AslThemeSwatch("light", "GitHub Light", listOf(Color(0xFFFFFFFF), Color(0xFFCF222E), Color(0xFF0A3069))),
    AslThemeSwatch("hc", "High Contrast", listOf(Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF34D399))),
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
                    .padding(16.dp),
            ) {
                EditorFontFamilyRow(colors = colors)
                EditorFontSizeSlider(uiState = uiState, interactionListener = interactionListener, colors = colors)
                AslThemeSwatchPicker(
                    label = "Color scheme",
                    swatches = COLOR_SCHEME_SWATCHES,
                    value = uiState.colorSchemeId,
                    onValueChange = { interactionListener.onColorSchemeChanged(it) },
                    modifier = Modifier.padding(top = 18.dp),
                )
                EditorTabSizeSection(uiState = uiState, interactionListener = interactionListener, colors = colors)
                EditorBehaviorSection(uiState = uiState, interactionListener = interactionListener, colors = colors)
                EditorLanguageServersSection(uiState = uiState, interactionListener = interactionListener, colors = colors)
            }
        }
    }
}

@Composable
private fun EditorFontFamilyRow(colors: AslColorScheme) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg),
    ) {
        AslListItem(
            title = "Font family",
            subtitle = "JetBrains Mono",
            icon = "type",
            divider = false,
            trailing = { Text(text = "built-in", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary) },
        )
    }
}

@Composable
private fun EditorFontSizeSlider(
    uiState: EditorSettingsUiState,
    interactionListener: EditorSettingsInteractionListener,
    colors: AslColorScheme,
) {
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
            color = colors.textPrimary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .background(colors.editorCanvas, AslShape.sm)
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

@Composable
private fun EditorLanguageServersSection(
    uiState: EditorSettingsUiState,
    interactionListener: EditorSettingsInteractionListener,
    colors: AslColorScheme,
) {
    HubSectionHeader("Language servers")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg)
            .padding(horizontal = 16.dp),
    ) {
        AslSwitch(
            label = "Kotlin LSP",
            checked = uiState.kotlinLsp,
            onCheckedChange = { interactionListener.onToggleKotlinLsp(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        AslSwitch(
            label = "Java LSP",
            checked = uiState.javaLsp,
            onCheckedChange = { interactionListener.onToggleJavaLsp(it) },
            modifier = Modifier.fillMaxWidth(),
        )
        AslSwitch(
            label = "XML LSP",
            checked = uiState.xmlLsp,
            onCheckedChange = { interactionListener.onToggleXmlLsp(it) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
