package com.example.androidstudiolite.feature.settings.root.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.example.androidstudiolite.core.designsystem.component.content.AslListItem
import com.example.androidstudiolite.core.designsystem.component.inputs.AslSearchField
import com.example.androidstudiolite.core.designsystem.component.navigation.AslTopAppBar
import com.example.androidstudiolite.core.designsystem.icon.AslIcon
import com.example.androidstudiolite.core.designsystem.theme.AslShape
import com.example.androidstudiolite.core.designsystem.theme.AslTheme
import com.example.androidstudiolite.feature.hub.components.HubSectionHeader
import com.example.androidstudiolite.feature.settings.root.interaction.SettingsRootInteraction
import com.example.androidstudiolite.feature.settings.root.uiState.SettingsRootUiState
import com.example.androidstudiolite.feature.settings.root.viewModel.SettingsRootViewModel

private data class SettingsRow(val title: String, val subtitle: String?, val icon: String, val onClick: () -> Unit = {})

@Composable
fun SettingsRootRoute(
    onBack: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenAiAgent: () -> Unit,
    onOpenBuildRun: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    viewModel: SettingsRootViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SettingsRootScreen(
        uiState = uiState,
        onInteraction = viewModel::onInteraction,
        onBack = onBack,
        onOpenGeneral = onOpenGeneral,
        onOpenEditor = onOpenEditor,
        onOpenAiAgent = onOpenAiAgent,
        onOpenBuildRun = onOpenBuildRun,
        onOpenAbout = onOpenAbout,
        onOpenTerminal = onOpenTerminal,
        onOpenDeveloperOptions = onOpenDeveloperOptions,
    )
}

@Composable
private fun SettingsRootScreen(
    uiState: SettingsRootUiState,
    onInteraction: (SettingsRootInteraction) -> Unit,
    onBack: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenAiAgent: () -> Unit,
    onOpenBuildRun: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
) {
    val colors = AslTheme.colors
    val configureRows = listOf(
        SettingsRow("General", "Appearance, language, startup", "sliders-horizontal", onOpenGeneral),
        SettingsRow("Editor", "Font, color scheme, LSP", "file-code", onOpenEditor),
        SettingsRow("AI Agent", "Providers & API keys", "sparkles", onOpenAiAgent),
        SettingsRow("Build & Run", "Gradle, install options", "hammer", onOpenBuildRun),
        SettingsRow("Terminal", "Shell, extra keys", "terminal", onOpenTerminal),
    )
    val privacyRows = listOf(
        SettingsRow("Statistics", "Anonymous usage sharing — ${if (uiState.shareUsageStats) "on" else "off"}", "chart-no-axes-column"),
    )
    val advancedRows = listOf(
        SettingsRow("Developer options", null, "wrench", onOpenDeveloperOptions),
        SettingsRow("About", "v1.2.0", "info", onOpenAbout),
    )

    fun matches(row: SettingsRow) = uiState.query.isBlank() || row.title.contains(uiState.query, ignoreCase = true)

    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AslTopAppBar(title = "Preferences", onBack = onBack)
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                AslSearchField(
                    value = uiState.query,
                    onValueChange = { onInteraction(SettingsRootInteraction.QueryChanged(it)) },
                    placeholder = "Search settings",
                )
                listOf("Configure" to configureRows, "Privacy" to privacyRows, "Advanced" to advancedRows).forEach { (header, rows) ->
                    val visible = rows.filter(::matches)
                    if (visible.isNotEmpty()) {
                        HubSectionHeader(header)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(colors.surface, AslShape.lg)
                                .border(1.dp, colors.borderDefault, AslShape.lg),
                        ) {
                            visible.forEachIndexed { index, row ->
                                AslListItem(
                                    title = row.title,
                                    subtitle = row.subtitle,
                                    icon = row.icon,
                                    divider = index != visible.lastIndex,
                                    trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
                                    onClick = row.onClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
