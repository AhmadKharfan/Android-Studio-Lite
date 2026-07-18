package com.ahmadkharfan.androidstudiolite.feature.settings.root
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel
import com.ahmadkharfan.androidstudiolite.R
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSearchField
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.hub.components.HubSectionHeader
import com.ahmadkharfan.androidstudiolite.feature.settings.root.SettingsRootInteractionListener
import com.ahmadkharfan.androidstudiolite.feature.settings.root.SettingsRootUiState
import com.ahmadkharfan.androidstudiolite.feature.settings.root.SettingsRootViewModel

private data class SettingsRow(val title: String, val subtitle: String?, val icon: String, val onClick: () -> Unit = {})

@Composable
fun SettingsRootRoute(
    onBack: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenAiAgent: () -> Unit,
    onOpenBuildRun: () -> Unit,
    onOpenServer: () -> Unit,
    onOpenGitAuth: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
    viewModel: SettingsRootViewModel = koinViewModel(),
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    SettingsRootScreen(
        uiState = uiState,
        interactionListener = viewModel,
        onBack = onBack,
        onOpenGeneral = onOpenGeneral,
        onOpenEditor = onOpenEditor,
        onOpenAiAgent = onOpenAiAgent,
        onOpenBuildRun = onOpenBuildRun,
        onOpenServer = onOpenServer,
        onOpenGitAuth = onOpenGitAuth,
        onOpenAbout = onOpenAbout,
        onOpenTerminal = onOpenTerminal,
        onOpenDeveloperOptions = onOpenDeveloperOptions,
    )
}

@Composable
private fun buildSettingsSections(
    onOpenGeneral: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenAiAgent: () -> Unit,
    onOpenBuildRun: () -> Unit,
    onOpenServer: () -> Unit,
    onOpenGitAuth: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
): List<Pair<String, List<SettingsRow>>> {
    val configureRows = listOf(
        SettingsRow(stringResource(R.string.settings_general), stringResource(R.string.settings_general_sub), "sliders-horizontal", onOpenGeneral),
        SettingsRow(stringResource(R.string.settings_editor), stringResource(R.string.settings_editor_sub), "file-code", onOpenEditor),
        SettingsRow(stringResource(R.string.settings_ai_agent), stringResource(R.string.settings_ai_agent_sub), "sparkles", onOpenAiAgent),
        SettingsRow(stringResource(R.string.settings_build_run), stringResource(R.string.settings_build_run_sub), "hammer", onOpenBuildRun),
        SettingsRow(stringResource(R.string.settings_git_auth), stringResource(R.string.settings_git_auth_sub), "git-branch", onOpenGitAuth),
        SettingsRow(stringResource(R.string.settings_server), stringResource(R.string.settings_server_sub), "server", onOpenServer),
        SettingsRow(stringResource(R.string.settings_terminal), stringResource(R.string.settings_terminal_sub), "terminal", onOpenTerminal),
    )
    val advancedRows = listOf(
        SettingsRow(stringResource(R.string.settings_developer), null, "wrench", onOpenDeveloperOptions),
        SettingsRow(stringResource(R.string.settings_about), null, "info", onOpenAbout),
    )
    return listOf(stringResource(R.string.settings_section_configure) to configureRows, stringResource(R.string.settings_section_advanced) to advancedRows)
}

@Composable
private fun SettingsRootScreen(
    uiState: SettingsRootUiState,
    interactionListener: SettingsRootInteractionListener,
    onBack: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenAiAgent: () -> Unit,
    onOpenBuildRun: () -> Unit,
    onOpenServer: () -> Unit,
    onOpenGitAuth: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenDeveloperOptions: () -> Unit,
) {
    val colors = AslTheme.colors
    val sections = buildSettingsSections(
        onOpenGeneral = onOpenGeneral,
        onOpenEditor = onOpenEditor,
        onOpenAiAgent = onOpenAiAgent,
        onOpenBuildRun = onOpenBuildRun,
        onOpenServer = onOpenServer,
        onOpenGitAuth = onOpenGitAuth,
        onOpenAbout = onOpenAbout,
        onOpenTerminal = onOpenTerminal,
        onOpenDeveloperOptions = onOpenDeveloperOptions,
    )

    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AslTopAppBar(title = stringResource(R.string.settings_title), onBack = onBack)
            SettingsRootContent(uiState = uiState, interactionListener = interactionListener, sections = sections, colors = colors)
        }
    }
}

@Composable
private fun SettingsRootContent(
    uiState: SettingsRootUiState,
    interactionListener: SettingsRootInteractionListener,
    sections: List<Pair<String, List<SettingsRow>>>,
    colors: AslColorScheme,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        AslSearchField(
            value = uiState.query,
            onValueChange = { interactionListener.onQueryChanged(it) },
            placeholder = stringResource(R.string.settings_search_placeholder),
        )
        sections.forEach { (header, rows) ->
            val visible = rows.filter { uiState.query.isBlank() || it.title.contains(uiState.query, ignoreCase = true) }
            if (visible.isNotEmpty()) {
                SettingsRowGroup(header = header, rows = visible, colors = colors)
            }
        }
    }
}

@Composable
private fun SettingsRowGroup(
    header: String,
    rows: List<SettingsRow>,
    colors: AslColorScheme,
) {
    HubSectionHeader(header)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface, AslShape.lg)
            .border(1.dp, colors.borderDefault, AslShape.lg),
    ) {
        rows.forEachIndexed { index, row ->
            AslListItem(
                title = row.title,
                subtitle = row.subtitle,
                icon = row.icon,
                divider = index != rows.lastIndex,
                trailing = { AslIcon(name = "chevron-right", size = 16.dp, tint = colors.textTertiary) },
                onClick = row.onClick,
            )
        }
    }
}
