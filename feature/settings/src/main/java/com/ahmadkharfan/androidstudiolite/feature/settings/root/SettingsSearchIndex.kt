package com.ahmadkharfan.androidstudiolite.feature.settings.root

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ahmadkharfan.androidstudiolite.core.common.R as CommonR
import com.ahmadkharfan.androidstudiolite.domain.model.AiProviderCatalog
import com.ahmadkharfan.androidstudiolite.feature.settings.R

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
            keywords = stringResource(R.string.settings_search_ui_mode_keywords),
            icon = "sliders-horizontal",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(CommonR.string.general_accent),
            breadcrumb = general,
            keywords = stringResource(R.string.settings_search_accent_keywords),
            icon = "sliders-horizontal",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(CommonR.string.general_auto_open_last),
            breadcrumb = general,
            keywords = stringResource(R.string.settings_search_startup_keywords),
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
            title = stringResource(R.string.settings_editor_font_family),
            breadcrumb = editor,
            keywords = stringResource(R.string.settings_search_editor_keywords),
            icon = "file-code",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.settings_editor_font_size),
            breadcrumb = editor,
            keywords = stringResource(R.string.settings_search_font_size_keywords),
            icon = "file-code",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.settings_editor_color_scheme),
            breadcrumb = editor,
            keywords = stringResource(R.string.settings_search_color_scheme_keywords),
            icon = "file-code",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.settings_editor_tab_size),
            breadcrumb = editor,
            keywords = stringResource(R.string.settings_search_tab_size_keywords),
            icon = "file-code",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.settings_editor_auto_save),
            breadcrumb = stringResource(R.string.settings_search_breadcrumb, editor, stringResource(R.string.settings_editor_behavior)),
            keywords = stringResource(R.string.settings_search_auto_save_keywords),
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
            keywords = stringResource(R.string.settings_search_ai_enable_keywords),
            icon = "sparkles",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(CommonR.string.ai_agent_auto_apply),
            breadcrumb = aiAgent,
            keywords = stringResource(R.string.settings_search_ai_apply_keywords),
            icon = "sparkles",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(CommonR.string.ai_chat_model),
            breadcrumb = aiAgent,
            keywords = stringResource(R.string.settings_search_ai_model_keywords),
            icon = "sparkles",
            onClick = onOpen,
        ),
    )
    val providerEntries = AiProviderCatalog.all.flatMap { provider ->
        listOf(
            SettingsSearchEntry(
                title = provider.name,
                breadcrumb = aiAgent,
                keywords = stringResource(R.string.settings_search_provider_keywords, provider.description, provider.id),
                icon = provider.icon,
                onClick = onOpen,
            ),
            SettingsSearchEntry(
                title = stringResource(R.string.settings_search_provider_api_key, provider.name),
                breadcrumb = aiAgent,
                keywords = stringResource(R.string.settings_search_provider_token_keywords, provider.name),
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
            title = stringResource(R.string.settings_build_aab_release),
            breadcrumb = stringResource(R.string.settings_search_breadcrumb, buildRun, stringResource(R.string.settings_build_output_format)),
            keywords = stringResource(R.string.settings_search_aab_keywords),
            icon = "hammer",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.settings_build_debug_keystore),
            breadcrumb = stringResource(R.string.settings_search_breadcrumb, buildRun, stringResource(R.string.settings_build_signing)),
            keywords = stringResource(R.string.settings_search_debug_keystore_keywords),
            icon = "hammer",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.settings_build_release_keystore),
            breadcrumb = stringResource(R.string.settings_search_breadcrumb, buildRun, stringResource(R.string.settings_build_signing)),
            keywords = stringResource(R.string.settings_search_release_keystore_keywords),
            icon = "hammer",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.settings_build_launch_after_install),
            breadcrumb = stringResource(R.string.settings_search_breadcrumb, buildRun, stringResource(R.string.settings_build_after_build)),
            keywords = stringResource(R.string.settings_search_launch_keywords),
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
            title = stringResource(R.string.settings_git_account),
            breadcrumb = gitAuth,
            keywords = stringResource(R.string.settings_search_git_account_keywords),
            icon = "github",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.settings_search_git_author_name),
            breadcrumb = stringResource(R.string.settings_search_breadcrumb, gitAuth, stringResource(R.string.settings_git_author)),
            keywords = stringResource(R.string.settings_search_git_author_name_keywords),
            icon = "git-branch",
            onClick = onOpen,
        ),
        SettingsSearchEntry(
            title = stringResource(R.string.settings_search_git_author_email),
            breadcrumb = stringResource(R.string.settings_search_breadcrumb, gitAuth, stringResource(R.string.settings_git_author)),
            keywords = stringResource(R.string.settings_search_git_author_email_keywords),
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
            keywords = stringResource(R.string.settings_search_about_keywords),
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
            keywords = stringResource(R.string.settings_search_contributors_keywords),
            icon = "users",
            onClick = onOpen,
        ),
    )
}
