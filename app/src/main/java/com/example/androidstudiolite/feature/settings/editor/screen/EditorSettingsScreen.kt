package com.example.androidstudiolite.feature.settings.editor.screen

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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androidstudiolite.core.designsystem.component.content.AslListItem
import com.example.androidstudiolite.core.designsystem.component.ide.AslThemeSwatch
import com.example.androidstudiolite.core.designsystem.component.ide.AslThemeSwatchPicker
import com.example.androidstudiolite.core.designsystem.component.inputs.AslSegmentedButton
import com.example.androidstudiolite.core.designsystem.component.inputs.AslSegmentedOption
import com.example.androidstudiolite.core.designsystem.component.inputs.AslSlider
import com.example.androidstudiolite.core.designsystem.component.inputs.AslSwitch
import com.example.androidstudiolite.core.designsystem.component.navigation.AslTopAppBar
import com.example.androidstudiolite.core.designsystem.theme.AslCode
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.hub.components.HubSectionHeader
import com.example.androidstudiolite.feature.settings.editor.interaction.EditorSettingsInteraction
import com.example.androidstudiolite.feature.settings.editor.uiState.EditorSettingsUiState
import com.example.androidstudiolite.feature.settings.editor.viewModel.EditorSettingsViewModel

private val COLOR_SCHEME_SWATCHES = listOf(
    AslThemeSwatch("darcula", "Darcula", listOf(Color(0xFF1E1E1E), Color(0xFFCC7832), Color(0xFF6A8759))),
    AslThemeSwatch("light", "GitHub Light", listOf(Color(0xFFFFFFFF), Color(0xFFCF222E), Color(0xFF0A3069))),
    AslThemeSwatch("hc", "High Contrast", listOf(Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF34D399))),
)

@Composable
fun EditorSettingsRoute(
    onBack: () -> Unit,
    viewModel: EditorSettingsViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    EditorSettingsScreen(uiState = uiState, onInteraction = viewModel::onInteraction, onBack = onBack)
}

@Composable
private fun EditorSettingsScreen(
    uiState: EditorSettingsUiState,
    onInteraction: (EditorSettingsInteraction) -> Unit,
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
                Column(modifier = Modifier.padding(top = 18.dp)) {
                    AslSlider(
                        value = uiState.fontSize.toFloat(),
                        onValueChange = { onInteraction(EditorSettingsInteraction.FontSizeChanged(it.toInt())) },
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
                AslThemeSwatchPicker(
                    label = "Color scheme",
                    swatches = COLOR_SCHEME_SWATCHES,
                    value = uiState.colorSchemeId,
                    onValueChange = { onInteraction(EditorSettingsInteraction.ColorSchemeChanged(it)) },
                    modifier = Modifier.padding(top = 18.dp),
                )
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
                    onValueChange = { onInteraction(EditorSettingsInteraction.TabSizeChanged(it.toInt())) },
                    fullWidth = true,
                )
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
                        onCheckedChange = { onInteraction(EditorSettingsInteraction.ToggleAutoSave(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
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
                        onCheckedChange = { onInteraction(EditorSettingsInteraction.ToggleKotlinLsp(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AslSwitch(
                        label = "Java LSP",
                        checked = uiState.javaLsp,
                        onCheckedChange = { onInteraction(EditorSettingsInteraction.ToggleJavaLsp(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AslSwitch(
                        label = "XML LSP",
                        checked = uiState.xmlLsp,
                        onCheckedChange = { onInteraction(EditorSettingsInteraction.ToggleXmlLsp(it)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
