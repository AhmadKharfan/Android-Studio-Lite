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
import com.ahmadkharfan.androidstudiolite.core.common.R as CommonR
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSearchField
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.designsystem.layout.aslImePadding
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.hub.components.HubSectionHeader
import com.ahmadkharfan.androidstudiolite.feature.settings.root.SettingsRootInteractionListener
import com.ahmadkharfan.androidstudiolite.feature.settings.root.SettingsRootUiState
import com.ahmadkharfan.androidstudiolite.feature.settings.root.SettingsRootViewModel

private data class SettingsRow(val title: String, val subtitle: String?, val icon: String, val onClick: () -> Unit = {})

private fun SettingsSearchEntry.toRow(): SettingsRow = SettingsRow(
    title = title,
    subtitle = breadcrumb,
    icon = icon,
    onClick = onClick,
)

@Composable
fun SettingsRootRoute(
    onBack: () -> Unit,
    onOpenGeneral: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenAiAgent: () -> Unit,
    onOpenBuildRun: () -> Unit,
    onOpenGitAuth: () -> Unit,
    onOpenAbout: () -> Unit,
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
        onOpenGitAuth = onOpenGitAuth,
        onOpenAbout = onOpenAbout,
    )
}

@Composable
private fun buildSettingsSections(
    onOpenGeneral: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenAiAgent: () -> Unit,
    onOpenBuildRun: () -> Unit,
    onOpenGitAuth: () -> Unit,
    onOpenAbout: () -> Unit,
): List<Pair<String, List<SettingsRow>>> {
    val configureRows = listOf(
        SettingsRow(stringResource(CommonR.string.settings_general), stringResource(CommonR.string.settings_general_sub), "sliders-horizontal", onOpenGeneral),
        SettingsRow(stringResource(CommonR.string.settings_editor), stringResource(CommonR.string.settings_editor_sub), "file-code", onOpenEditor),
        SettingsRow(stringResource(CommonR.string.settings_ai_agent), stringResource(CommonR.string.settings_ai_agent_sub), "sparkles", onOpenAiAgent),
        SettingsRow(stringResource(CommonR.string.settings_build_run), stringResource(CommonR.string.settings_build_run_sub), "hammer", onOpenBuildRun),
        SettingsRow(stringResource(CommonR.string.settings_git_auth), stringResource(CommonR.string.settings_git_auth_sub), "git-branch", onOpenGitAuth),
    )
    val advancedRows = listOf(
        SettingsRow(stringResource(CommonR.string.settings_about), null, "info", onOpenAbout),
    )
    return listOf(stringResource(CommonR.string.settings_section_configure) to configureRows, stringResource(CommonR.string.settings_section_advanced) to advancedRows)
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
    onOpenGitAuth: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val colors = AslTheme.colors
    val sections = buildSettingsSections(
        onOpenGeneral = onOpenGeneral,
        onOpenEditor = onOpenEditor,
        onOpenAiAgent = onOpenAiAgent,
        onOpenBuildRun = onOpenBuildRun,
        onOpenGitAuth = onOpenGitAuth,
        onOpenAbout = onOpenAbout,
    )
    val searchIndex = buildSettingsSearchIndex(
        onOpenGeneral = onOpenGeneral,
        onOpenEditor = onOpenEditor,
        onOpenAiAgent = onOpenAiAgent,
        onOpenBuildRun = onOpenBuildRun,
        onOpenGitAuth = onOpenGitAuth,
        onOpenAbout = onOpenAbout,
    )

    Scaffold(containerColor = colors.bgBase) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AslTopAppBar(title = stringResource(CommonR.string.settings_title), onBack = onBack)
            SettingsRootContent(
                uiState = uiState,
                interactionListener = interactionListener,
                sections = sections,
                searchIndex = searchIndex,
                colors = colors,
            )
        }
    }
}

@Composable
private fun SettingsRootContent(
    uiState: SettingsRootUiState,
    interactionListener: SettingsRootInteractionListener,
    sections: List<Pair<String, List<SettingsRow>>>,
    searchIndex: List<SettingsSearchEntry>,
    colors: AslColorScheme,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .aslImePadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        AslSearchField(
            value = uiState.query,
            onValueChange = { interactionListener.onQueryChanged(it) },
            placeholder = stringResource(CommonR.string.settings_search_placeholder),
        )
        if (uiState.query.isBlank()) {
            sections.forEach { (header, rows) ->
                SettingsRowGroup(header = header, rows = rows, colors = colors)
            }
        } else {
            val results = searchIndex
                .filter { it.matches(uiState.query) }
                .distinctBy { "${it.title}\n${it.breadcrumb}" }
                .map { it.toRow() }
            if (results.isEmpty()) {
                HubSectionHeader(stringResource(CommonR.string.settings_search_no_results))
            } else {
                SettingsRowGroup(header = stringResource(CommonR.string.settings_search_results), rows = results, colors = colors)
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
