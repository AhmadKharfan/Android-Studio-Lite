package com.ahmadkharfan.androidstudiolite.feature.settings.root

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ahmadkharfan.androidstudiolite.R
import com.ahmadkharfan.androidstudiolite.data.ai.AiProviderCatalog

data class SettingsSearchEntry(
    val title: String,
    val breadcrumb: String,
    val keywords: String = "",
    val icon: String,
    val onClick: () -> Unit,
) {
    fun matches(query: String): Boolean {
        val needle = query.trim()
        if (needle.isBlank()) return false
        return "$title $breadcrumb $keywords".contains(needle, ignoreCase = true)
    }
}

@Composable
fun buildSettingsSearchIndex(
    onOpenGeneral: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenAiAgent: () -> Unit,
    onOpenBuildRun: () -> Unit,
    onOpenGitAuth: () -> Unit,
    onOpenAbout: () -> Unit,
): List<SettingsSearchEntry> {
    val general = stringResource(R.string.settings_general)
    val editor = stringResource(R.string.settings_editor)
    val aiAgent = stringResource(R.string.settings_ai_agent)
    val buildRun = stringResource(R.string.settings_build_run)
    val gitAuth = stringResource(R.string.settings_git_auth)
    val about = stringResource(R.string.settings_about)

    return listOf(
        SettingsSearchEntry(
            title = general,
            breadcrumb = stringResource(R.string.settings_section_configure),
            keywords = stringResource(R.string.settings_general_sub),
            icon = "sliders-horizontal",
            onClick = onOpenGeneral,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.general_ui_mode),
            breadcrumb = general,
            keywords = "theme light dark system appearance mode",
            icon = "sliders-horizontal",
            onClick = onOpenGeneral,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.general_accent),
            breadcrumb = general,
            keywords = "color emerald fjord amber accent",
            icon = "sliders-horizontal",
            onClick = onOpenGeneral,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.general_auto_open_last),
            breadcrumb = general,
            keywords = "startup resume last project open",
            icon = "sliders-horizontal",
            onClick = onOpenGeneral,
        ),

        SettingsSearchEntry(
            title = editor,
            breadcrumb = stringResource(R.string.settings_section_configure),
            keywords = stringResource(R.string.settings_editor_sub),
            icon = "file-code",
            onClick = onOpenEditor,
        ),
        SettingsSearchEntry(
            title = "Font family",
            breadcrumb = editor,
            keywords = "jetbrains mono monospace typeface",
            icon = "file-code",
            onClick = onOpenEditor,
        ),
        SettingsSearchEntry(
            title = "Font size",
            breadcrumb = editor,
            keywords = "text size sp zoom",
            icon = "file-code",
            onClick = onOpenEditor,
        ),
        SettingsSearchEntry(
            title = "Color scheme",
            breadcrumb = editor,
            keywords = "darcula syntax theme highlight github light contrast",
            icon = "file-code",
            onClick = onOpenEditor,
        ),
        SettingsSearchEntry(
            title = "Tab size",
            breadcrumb = editor,
            keywords = "indent spaces 2 4",
            icon = "file-code",
            onClick = onOpenEditor,
        ),
        SettingsSearchEntry(
            title = "Auto-save",
            breadcrumb = "$editor · Behavior",
            keywords = "save file autosave",
            icon = "file-code",
            onClick = onOpenEditor,
        ),

        SettingsSearchEntry(
            title = aiAgent,
            breadcrumb = stringResource(R.string.settings_section_configure),
            keywords = stringResource(R.string.settings_ai_agent_sub),
            icon = "sparkles",
            onClick = onOpenAiAgent,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.ai_agent_enable),
            breadcrumb = aiAgent,
            keywords = "agent chat assistant",
            icon = "sparkles",
            onClick = onOpenAiAgent,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.ai_agent_auto_apply),
            breadcrumb = aiAgent,
            keywords = "confirm apply changes tools",
            icon = "sparkles",
            onClick = onOpenAiAgent,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.ai_agent_instructions),
            breadcrumb = aiAgent,
            keywords = "system prompt rules",
            icon = "sparkles",
            onClick = onOpenAiAgent,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.ai_chat_model),
            breadcrumb = aiAgent,
            keywords = "llm model provider",
            icon = "sparkles",
            onClick = onOpenAiAgent,
        ),
    ) + AiProviderCatalog.all.flatMap { provider ->
        listOf(
            SettingsSearchEntry(
                title = provider.name,
                breadcrumb = aiAgent,
                keywords = "${provider.description} api key provider ${provider.id}",
                icon = provider.icon,
                onClick = onOpenAiAgent,
            ),
            SettingsSearchEntry(
                title = "${provider.name} API key",
                breadcrumb = aiAgent,
                keywords = "token credential ${provider.name}",
                icon = provider.icon,
                onClick = onOpenAiAgent,
            ),
        )
    } + listOf(
        SettingsSearchEntry(
            title = buildRun,
            breadcrumb = stringResource(R.string.settings_section_configure),
            keywords = stringResource(R.string.settings_build_run_sub),
            icon = "hammer",
            onClick = onOpenBuildRun,
        ),
        SettingsSearchEntry(
            title = "Build App Bundle (.aab) for release",
            breadcrumb = "$buildRun · Output format",
            keywords = "aab bundle play store output apk",
            icon = "hammer",
            onClick = onOpenBuildRun,
        ),
        SettingsSearchEntry(
            title = "Build from Git remote when available",
            breadcrumb = "$buildRun · Build source",
            keywords = "git clone remote upload zip",
            icon = "hammer",
            onClick = onOpenBuildRun,
        ),
        SettingsSearchEntry(
            title = "Debug keystore",
            breadcrumb = "$buildRun · Signing",
            keywords = "signing certificate debug",
            icon = "hammer",
            onClick = onOpenBuildRun,
        ),
        SettingsSearchEntry(
            title = "Release keystore",
            breadcrumb = "$buildRun · Signing",
            keywords = "signing certificate release import create",
            icon = "hammer",
            onClick = onOpenBuildRun,
        ),
        SettingsSearchEntry(
            title = "Launch app after install",
            breadcrumb = "$buildRun · After build",
            keywords = "run open launch install",
            icon = "hammer",
            onClick = onOpenBuildRun,
        ),

        SettingsSearchEntry(
            title = gitAuth,
            breadcrumb = stringResource(R.string.settings_section_configure),
            keywords = stringResource(R.string.settings_git_auth_sub),
            icon = "git-branch",
            onClick = onOpenGitAuth,
        ),
        SettingsSearchEntry(
            title = "GitHub account",
            breadcrumb = gitAuth,
            keywords = "sign in github token oauth connect",
            icon = "github",
            onClick = onOpenGitAuth,
        ),
        SettingsSearchEntry(
            title = "Git author name",
            breadcrumb = "$gitAuth · Git author",
            keywords = "commit author name identity",
            icon = "git-branch",
            onClick = onOpenGitAuth,
        ),
        SettingsSearchEntry(
            title = "Git author email",
            breadcrumb = "$gitAuth · Git author",
            keywords = "commit email identity",
            icon = "git-branch",
            onClick = onOpenGitAuth,
        ),

        SettingsSearchEntry(
            title = about,
            breadcrumb = stringResource(R.string.settings_section_advanced),
            keywords = "version app info",
            icon = "info",
            onClick = onOpenAbout,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.about_github),
            breadcrumb = about,
            keywords = stringResource(R.string.about_github_sub),
            icon = "github",
            onClick = onOpenAbout,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.about_contributors),
            breadcrumb = about,
            keywords = "contributors credits",
            icon = "users",
            onClick = onOpenAbout,
        ),
    )
}
