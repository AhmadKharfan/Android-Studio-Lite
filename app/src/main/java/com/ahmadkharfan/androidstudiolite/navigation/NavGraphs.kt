package com.ahmadkharfan.androidstudiolite.navigation

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget
import com.ahmadkharfan.androidstudiolite.feature.acsmissing.AcsMissingRoute
import com.ahmadkharfan.androidstudiolite.feature.blockingerror.BlockingErrorRoute
import com.ahmadkharfan.androidstudiolite.feature.blockingerror.BlockingErrorType
import com.ahmadkharfan.androidstudiolite.feature.crashreport.CrashReportRoute
import com.ahmadkharfan.androidstudiolite.feature.onboarding.navigation.OnboardingRoutes
import com.ahmadkharfan.androidstudiolite.feature.onboarding.navigation.onboardingGraph
import com.ahmadkharfan.androidstudiolite.feature.projects.navigation.ProjectsRoutes
import com.ahmadkharfan.androidstudiolite.feature.projects.navigation.projectsGraph
import com.ahmadkharfan.androidstudiolite.feature.settings.navigation.SettingsRoutes
import com.ahmadkharfan.androidstudiolite.feature.settings.navigation.settingsGraph
import com.ahmadkharfan.androidstudiolite.feature.terminal.navigation.terminalGraph
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorNavigation
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorRoute
import com.ahmadkharfan.androidstudiolite.feature.editor.git.conflict.GitConflictRoute
import com.ahmadkharfan.androidstudiolite.feature.editor.git.diff.GitDiffRoute
import com.ahmadkharfan.androidstudiolite.feature.editor.git.history.GitBlameRoute
import com.ahmadkharfan.androidstudiolite.feature.editor.git.history.GitHistoryRoute
import com.ahmadkharfan.androidstudiolite.feature.editor.git.refs.GitRefsMode
import com.ahmadkharfan.androidstudiolite.feature.editor.git.refs.GitRefsRoute

internal fun NavGraphBuilder.onboardingGraph(navController: NavHostController) {
    onboardingGraph(
        navigateTo = { route -> navController.navigate(route) },
        onFinished = {
            navController.navigate(ProjectsRoutes.HUB) {
                popUpTo(OnboardingRoutes.WELCOME) { inclusive = true }
            }
        },
    )
}

internal fun NavGraphBuilder.projectsGraph(navController: NavHostController) {
    projectsGraph(
        navigateTo = { route -> navController.navigate(route) },
        onOpenProject = { id -> navController.navigate(Routes.editor(id)) },
        onOpenPreferences = { navController.navigate(SettingsRoutes.ROOT) },
        onCreated = { id ->
            navController.navigate(Routes.editor(id)) { popUpTo(ProjectsRoutes.HUB) }
        },
        popBackTo = { route -> navController.popBackStack(route, inclusive = true) },
        popBack = { navController.popBackStack() },
        setPreviousResult = { key, value ->
            navController.previousBackStackEntry?.savedStateHandle?.set(key, value)
        },
    )
}

internal fun NavGraphBuilder.utilityGraph(navController: NavHostController) {
    terminalGraph(onBack = { navController.popBackStack() })

    composable(Routes.CRASH_REPORT) {
        CrashReportRoute(
            onRestart = { navController.popBackStack() },
            onClose = { navController.popBackStack() },
        )
    }
}

internal fun NavGraphBuilder.editorGraph(navController: NavHostController) {
    editorDestination(navController)
    gitDiffDestination(navController)
    gitHistoryDestination(navController)
    gitBlameDestination(navController)
    gitRefsDestination(navController)
    gitConflictsDestination(navController)
}

private fun NavGraphBuilder.editorDestination(navController: NavHostController) {
    composable(
        route = Routes.EDITOR_PATTERN,
        arguments = listOf(navArgument("projectId") { }),
    ) { backStackEntry ->
        val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
        val conflictPath by backStackEntry.savedStateHandle
            .getStateFlow<String?>("git_conflict_path", null).collectAsState()
        EditorRoute(
            projectId = projectId,
            navigation = EditorNavigation(
                onCloseProject = {
                    if (!navController.popBackStack(ProjectsRoutes.HUB, inclusive = false)) {
                        navController.navigate(ProjectsRoutes.HUB) {
                            popUpTo(navController.graph.id) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                },
                onOpenSettings = { navController.navigate(SettingsRoutes.ROOT) },
                onOpenAiAgentSettings = { navController.navigate(SettingsRoutes.AI_AGENT) },
                onOpenGitDiff = { path, target -> navController.navigate(Routes.gitDiff(projectId, path, target)) },
                onOpenGitHistory = { path -> navController.navigate(Routes.gitHistory(projectId, path)) },
                onOpenGitBlame = { path -> navController.navigate(Routes.gitBlame(projectId, path)) },
                onOpenBranches = { navController.navigate(Routes.gitRefs(projectId, GitRefsMode.BRANCHES.name)) },
                onOpenTags = { navController.navigate(Routes.gitRefs(projectId, GitRefsMode.TAGS.name)) },
                onOpenStashes = { navController.navigate(Routes.gitRefs(projectId, GitRefsMode.STASHES.name)) },
                onOpenHistory = { navController.navigate(Routes.gitHistory(projectId)) },
                onOpenConflicts = { navController.navigate(Routes.gitConflicts(projectId)) },
            ),
            openConflictPath = conflictPath,
            onConflictPathOpened = { backStackEntry.savedStateHandle["git_conflict_path"] = null },
        )
    }
}

private fun NavGraphBuilder.gitDiffDestination(navController: NavHostController) {
    composable(
        route = Routes.GIT_DIFF_PATTERN,
        arguments = listOf(
            navArgument("projectId") { },
            navArgument("target") { },
            navArgument("path") { },
            navArgument("commitId") { },
        ),
    ) { backStackEntry ->
        val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
        val path = backStackEntry.arguments?.getString("path").orEmpty()
        val commitId = backStackEntry.arguments?.getString("commitId")?.takeIf { it.isNotBlank() }
        val target = runCatching {
            GitDiffTarget.valueOf(backStackEntry.arguments?.getString("target").orEmpty())
        }.getOrDefault(GitDiffTarget.INDEX_TO_WORKTREE)
        GitDiffRoute(
            projectId = projectId,
            path = path,
            target = target,
            onBack = { navController.popBackStack() },
            commitId = commitId,
        )
    }
}

private fun NavGraphBuilder.gitHistoryDestination(navController: NavHostController) {
    composable(
        route = Routes.GIT_HISTORY_PATTERN,
        arguments = listOf(navArgument("projectId") { }, navArgument("path") { }),
    ) { backStackEntry ->
        val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
        val path = backStackEntry.arguments?.getString("path")?.takeIf { it.isNotBlank() }
        GitHistoryRoute(
            projectId = projectId,
            path = path,
            onBack = { navController.popBackStack() },
            onOpenDiff = { file, commit ->
                navController.navigate(
                    Routes.gitDiff(projectId, file, GitDiffTarget.COMMIT_TO_PARENT, commit),
                )
            },
        )
    }
}

private fun NavGraphBuilder.gitBlameDestination(navController: NavHostController) {
    composable(
        route = Routes.GIT_BLAME_PATTERN,
        arguments = listOf(navArgument("projectId") { }, navArgument("path") { }),
    ) { backStackEntry ->
        GitBlameRoute(
            projectId = backStackEntry.arguments?.getString("projectId").orEmpty(),
            path = backStackEntry.arguments?.getString("path").orEmpty(),
            onBack = { navController.popBackStack() },
        )
    }
}

private fun NavGraphBuilder.gitRefsDestination(navController: NavHostController) {
    composable(
        route = Routes.GIT_REFS_PATTERN,
        arguments = listOf(navArgument("projectId") { }, navArgument("mode") { }),
    ) { backStackEntry ->
        val mode = runCatching {
            GitRefsMode.valueOf(backStackEntry.arguments?.getString("mode").orEmpty())
        }.getOrDefault(GitRefsMode.BRANCHES)
        GitRefsRoute(
            projectId = backStackEntry.arguments?.getString("projectId").orEmpty(),
            mode = mode,
            onBack = { navController.popBackStack() },
        )
    }
}

private fun NavGraphBuilder.gitConflictsDestination(navController: NavHostController) {
    composable(
        route = Routes.GIT_CONFLICTS_PATTERN,
        arguments = listOf(navArgument("projectId") { }),
    ) { backStackEntry ->
        val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
        GitConflictRoute(
            projectId = projectId,
            onBack = { navController.popBackStack() },
            onOpenEditor = { path ->
                navController.previousBackStackEntry?.savedStateHandle?.set("git_conflict_path", path)
                navController.popBackStack()
            },
        )
    }
}

internal fun NavGraphBuilder.settingsGraph(navController: NavHostController) {
    settingsGraph(
        navigateTo = { route -> navController.navigate(route) },
        onBack = { navController.popBackStack() },
    )
}

internal fun NavGraphBuilder.deviceSupportGraph() {
    composable(Routes.ACS_MISSING) {
        AcsMissingRoute()
    }
    composable(
        route = Routes.BLOCKING_ERROR_PATTERN,
        arguments = listOf(navArgument("type") { }),
    ) { backStackEntry ->
        val type = when (backStackEntry.arguments?.getString("type")) {
            "sdcard" -> BlockingErrorType.SdCardInstall
            "user" -> BlockingErrorType.SecondaryUser
            else -> BlockingErrorType.UnsupportedDevice
        }
        BlockingErrorRoute(type = type)
    }
}
