package com.ahmadkharfan.androidstudiolite.feature.settings.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ahmadkharfan.androidstudiolite.feature.settings.about.AboutRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.aiagent.AiAgentSettingsRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.buildrun.BuildRunSettingsRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.editor.EditorSettingsRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.general.GeneralRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.gitauth.GitAuthSettingsRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.root.SettingsRootRoute

fun NavGraphBuilder.settingsGraph(
    navigateTo: (String) -> Unit,
    onBack: () -> Unit,
) {
    composable(SettingsRoutes.ROOT) {
        SettingsRootRoute(
            onBack = onBack,
            onOpenGeneral = { navigateTo(SettingsRoutes.GENERAL) },
            onOpenEditor = { navigateTo(SettingsRoutes.EDITOR) },
            onOpenAiAgent = { navigateTo(SettingsRoutes.AI_AGENT) },
            onOpenBuildRun = { navigateTo(SettingsRoutes.BUILD_RUN) },
            onOpenGitAuth = { navigateTo(SettingsRoutes.GIT_AUTH) },
            onOpenAbout = { navigateTo(SettingsRoutes.ABOUT) },
        )
    }
    composable(SettingsRoutes.GENERAL) { GeneralRoute(onBack = onBack) }
    composable(SettingsRoutes.EDITOR) { EditorSettingsRoute(onBack = onBack) }
    composable(SettingsRoutes.AI_AGENT) { AiAgentSettingsRoute(onBack = onBack) }
    composable(SettingsRoutes.BUILD_RUN) { BuildRunSettingsRoute(onBack = onBack) }
    composable(SettingsRoutes.GIT_AUTH) { GitAuthSettingsRoute(onBack = onBack) }
    composable(SettingsRoutes.ABOUT) { AboutRoute(onBack = onBack) }
}
