package com.ahmadkharfan.androidstudiolite.feature.settings

import androidx.compose.runtime.Composable
import com.ahmadkharfan.androidstudiolite.feature.settings.about.AboutRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.aiagent.AiAgentSettingsRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.api.SettingsFeatureApi
import com.ahmadkharfan.androidstudiolite.feature.settings.api.SettingsRoutes
import com.ahmadkharfan.androidstudiolite.feature.settings.buildrun.BuildRunSettingsRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.editor.EditorSettingsRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.general.GeneralRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.gitauth.GitAuthSettingsRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.root.SettingsRootRoute

internal class SettingsFeatureApiImpl : SettingsFeatureApi {

    @Composable
    override fun RootEntry(onBack: () -> Unit, onOpen: (route: String) -> Unit) = SettingsRootRoute(
        onBack = onBack,
        onOpenGeneral = { onOpen(SettingsRoutes.GENERAL) },
        onOpenEditor = { onOpen(SettingsRoutes.EDITOR) },
        onOpenAiAgent = { onOpen(SettingsRoutes.AI_AGENT) },
        onOpenBuildRun = { onOpen(SettingsRoutes.BUILD_RUN) },
        onOpenGitAuth = { onOpen(SettingsRoutes.GIT_AUTH) },
        onOpenAbout = { onOpen(SettingsRoutes.ABOUT) },
    )

    @Composable
    override fun GeneralEntry(onBack: () -> Unit) = GeneralRoute(onBack = onBack)

    @Composable
    override fun EditorEntry(onBack: () -> Unit) = EditorSettingsRoute(onBack = onBack)

    @Composable
    override fun AiAgentEntry(onBack: () -> Unit) = AiAgentSettingsRoute(onBack = onBack)

    @Composable
    override fun BuildRunEntry(onBack: () -> Unit) = BuildRunSettingsRoute(onBack = onBack)

    @Composable
    override fun GitAuthEntry(onBack: () -> Unit) = GitAuthSettingsRoute(onBack = onBack)

    @Composable
    override fun AboutEntry(onBack: () -> Unit) = AboutRoute(onBack = onBack)
}
