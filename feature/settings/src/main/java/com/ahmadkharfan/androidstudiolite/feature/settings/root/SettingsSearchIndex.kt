package com.ahmadkharfan.androidstudiolite.feature.settings.root

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ahmadkharfan.androidstudiolite.core.common.R as CommonR
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
): List<SettingsSearchEntry> =
    generalSearchEntries(onOpenGeneral) +
        editorSearchEntries(onOpenEditor) +
        aiAgentSearchEntries(onOpenAiAgent) +
        buildRunSearchEntries(onOpenBuildRun) +
        gitAuthSearchEntries(onOpenGitAuth) +
        aboutSearchEntries(onOpenAbout)

@Composable
private fun generalSearchEntries(onOpen: () -> Unit): List<SettingsSearchEntry> {
    val general = stringResource(CommonR.string.settings_general)
    return listOf(
        SettingsSearchEntry(
            title = general,
            breadcrumb = stringResource(CommonR.string.settings_section_configure),
            keywords = stringResource(CommonR.string.settings_general_sub),
            icon = "sliders-horizontal",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(CommonR.string.general_ui_mode),
            breadcrumb = general,
            keywords = "theme light dark system appearance mode",
            icon = "sliders-horizontal",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(CommonR.string.general_accent),
            breadcrumb = general,
            keywords = "color emerald fjord amber accent",
            icon = "sliders-horizontal",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(CommonR.string.general_auto_open_last),
            breadcrumb = general,
            keywords = "startup resume last project open",
            icon = "sliders-horizontal",
            onClick = onOpen,
        ),
    )
}

@Composable
private fun editorSearchEntries(onOpen: () -> Unit): List<SettingsSearchEntry> {
    val editor = stringResource(CommonR.string.settings_editor)
    return listOf(
        SettingsSearchEntry(
            title = editor,
            breadcrumb = stringResource(CommonR.string.settings_section_configure),
            keywords = stringResource(CommonR.string.settings_editor_sub),
            icon = "file-code",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = "Font family",
            breadcrumb = editor,
            keywords = "jetbrains mono monospace typeface",
            icon = "file-code",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = "Font size",
            breadcrumb = editor,
            keywords = "text size sp zoom",
            icon = "file-code",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = "Color scheme",
            breadcrumb = editor,
            keywords = "darcula syntax theme highlight github light contrast",
            icon = "file-code",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = "Tab size",
            breadcrumb = editor,
            keywords = "indent spaces 2 4",
            icon = "file-code",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = "Auto-save",
            breadcrumb = "$editor · Behavior",
            keywords = "save file autosave",
            icon = "file-code",
            onClick = onOpen,
        ),
    )
}

@Composable
private fun aiAgentSearchEntries(onOpen: () -> Unit): List<SettingsSearchEntry> {
    val aiAgent = stringResource(CommonR.string.settings_ai_agent)
    val fixedEntries = listOf(
        SettingsSearchEntry(
            title = aiAgent,
            breadcrumb = stringResource(CommonR.string.settings_section_configure),
            keywords = stringResource(CommonR.string.settings_ai_agent_sub),
            icon = "sparkles",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(CommonR.string.ai_agent_enable),
            breadcrumb = aiAgent,
            keywords = "agent chat assistant",
            icon = "sparkles",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(CommonR.string.ai_agent_auto_apply),
            breadcrumb = aiAgent,
            keywords = "confirm apply changes tools",
            icon = "sparkles",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(CommonR.string.ai_agent_instructions),
            breadcrumb = aiAgent,
            keywords = "system prompt rules",
            icon = "sparkles",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(CommonR.string.ai_chat_model),
            breadcrumb = aiAgent,
            keywords = "llm model provider",
            icon = "sparkles",
            onClick = onOpen,
        ),
    )
    val providerEntries = AiProviderCatalog.all.flatMap { provider ->
        listOf(
            SettingsSearchEntry(
                title = provider.name,
                breadcrumb = aiAgent,
                keywords = "${provider.description} api key provider ${provider.id}",
                icon = provider.icon,
                onClick = onOpen,
            ),
            SettingsSearchEntry(
                title = "${provider.name} API key",
                breadcrumb = aiAgent,
                keywords = "token credential ${provider.name}",
                icon = provider.icon,
                onClick = onOpen,
            ),
        )
    }
    return fixedEntries + providerEntries
}

@Composable
private fun buildRunSearchEntries(onOpen: () -> Unit): List<SettingsSearchEntry> {
    val buildRun = stringResource(CommonR.string.settings_build_run)
    return listOf(
        SettingsSearchEntry(
            title = buildRun,
            breadcrumb = stringResource(CommonR.string.settings_section_configure),
            keywords = stringResource(CommonR.string.settings_build_run_sub),
            icon = "hammer",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = "Build App Bundle (.aab) for release",
            breadcrumb = "$buildRun · Output format",
            keywords = "aab bundle play store output apk",
            icon = "hammer",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = "Debug keystore",
            breadcrumb = "$buildRun · Signing",
            keywords = "signing certificate debug",
            icon = "hammer",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = "Release keystore",
            breadcrumb = "$buildRun · Signing",
            keywords = "signing certificate release import create",
            icon = "hammer",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = "Launch app after install",
            breadcrumb = "$buildRun · After build",
            keywords = "run open launch install",
            icon = "hammer",
            onClick = onOpen,
        ),
    )
}

@Composable
private fun gitAuthSearchEntries(onOpen: () -> Unit): List<SettingsSearchEntry> {
    val gitAuth = stringResource(CommonR.string.settings_git_auth)
    return listOf(
        SettingsSearchEntry(
            title = gitAuth,
            breadcrumb = stringResource(CommonR.string.settings_section_configure),
            keywords = stringResource(CommonR.string.settings_git_auth_sub),
            icon = "git-branch",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = "GitHub account",
            breadcrumb = gitAuth,
            keywords = "sign in github token oauth connect",
            icon = "github",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = "Git author name",
            breadcrumb = "$gitAuth · Git author",
            keywords = "commit author name identity",
            icon = "git-branch",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = "Git author email",
            breadcrumb = "$gitAuth · Git author",
            keywords = "commit email identity",
            icon = "git-branch",
            onClick = onOpen,
        ),
    )
}

@Composable
private fun aboutSearchEntries(onOpen: () -> Unit): List<SettingsSearchEntry> {
    val about = stringResource(CommonR.string.settings_about)
    return listOf(
        SettingsSearchEntry(
            title = about,
            breadcrumb = stringResource(CommonR.string.settings_section_advanced),
            keywords = "version app info",
            icon = "info",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(CommonR.string.about_github),
            breadcrumb = about,
            keywords = stringResource(CommonR.string.about_github_sub),
            icon = "github",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(CommonR.string.about_contributors),
            breadcrumb = about,
            keywords = "contributors credits",
            icon = "users",
            onClick = onOpen,
        ),
    )
}
