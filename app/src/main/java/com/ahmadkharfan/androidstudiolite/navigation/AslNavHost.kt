package com.ahmadkharfan.androidstudiolite.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ahmadkharfan.androidstudiolite.feature.acsmissing.AcsMissingRoute
import com.ahmadkharfan.androidstudiolite.feature.blockingerror.BlockingErrorRoute
import com.ahmadkharfan.androidstudiolite.feature.blockingerror.BlockingErrorType
import com.ahmadkharfan.androidstudiolite.feature.createproject.CreateProjectRoute
import com.ahmadkharfan.androidstudiolite.feature.crashreport.CrashReportRoute
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorRoute
import com.ahmadkharfan.androidstudiolite.feature.editor.git.diff.GitDiffRoute
import com.ahmadkharfan.androidstudiolite.feature.editor.git.history.GitBlameRoute
import com.ahmadkharfan.androidstudiolite.feature.editor.git.history.GitHistoryRoute
import com.ahmadkharfan.androidstudiolite.feature.editor.git.refs.GitRefsMode
import com.ahmadkharfan.androidstudiolite.feature.editor.git.refs.GitRefsRoute
import com.ahmadkharfan.androidstudiolite.feature.editor.git.conflict.GitConflictRoute
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget
import com.ahmadkharfan.androidstudiolite.feature.folderpicker.FolderPickerRoute
import com.ahmadkharfan.androidstudiolite.feature.hub.HubRoute
import com.ahmadkharfan.androidstudiolite.feature.onboarding.complete.CompleteRoute
import com.ahmadkharfan.androidstudiolite.feature.onboarding.permissions.PermissionsRoute
import com.ahmadkharfan.androidstudiolite.feature.onboarding.welcome.WelcomeRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.about.AboutRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.aiagent.AiAgentSettingsRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.buildrun.BuildRunSettingsRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.developer.DeveloperOptionsRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.editor.EditorSettingsRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.general.GeneralRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.ideconfig.IdeConfigRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.root.SettingsRootRoute
import com.ahmadkharfan.androidstudiolite.feature.settings.server.ServerSettingsRoute
import com.ahmadkharfan.androidstudiolite.feature.terminal.TerminalRoute
import com.ahmadkharfan.androidstudiolite.feature.uidesigner.DesignerRoute

@Composable
fun AslNavHost(
    startDestination: String,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { aslEnter() },
        exitTransition = { aslExit() },
        popEnterTransition = { aslPopEnter() },
        popExitTransition = { aslPopExit() },
    ) {
        composable(Routes.ONBOARDING_WELCOME) {
            WelcomeRoute(onGetStarted = { navController.navigate(Routes.ONBOARDING_PERMISSIONS) })
        }
        // Permissions is the last onboarding gate: the Setup step used to install the on-device
        // toolchain, which no longer exists now that builds run on the server.
        composable(Routes.ONBOARDING_PERMISSIONS) {
            PermissionsRoute(onContinue = { navController.navigate(Routes.ONBOARDING_COMPLETE) })
        }
        composable(Routes.ONBOARDING_COMPLETE) {
            CompleteRoute(
                onOpenHub = {
                    navController.navigate(Routes.HUB) { popUpTo(Routes.ONBOARDING_WELCOME) { inclusive = true } }
                },
            )
        }

        composable(Routes.HUB) { backStackEntry ->
            val pickedFolder by backStackEntry.savedStateHandle
                .getStateFlow<String?>("picked_folder", null)
                .collectAsState()
            HubRoute(
                onOpenProject = { id -> navController.navigate(Routes.editor(id)) },
                onCreateProject = { navController.navigate(Routes.CREATE_PROJECT) },
                onOpenPreferences = { navController.navigate(Routes.SETTINGS_ROOT) },
                onOpenTerminal = { navController.navigate(Routes.TERMINAL) },
                onOpenIdeConfig = { navController.navigate(Routes.SETTINGS_IDE_CONFIG) },
                onOpenDocs = {},
                onBrowseFolder = { navController.navigate(Routes.FOLDER_PICKER) },
                pickedFolder = pickedFolder,
                onPickedFolderConsumed = { backStackEntry.savedStateHandle["picked_folder"] = null },
            )
        }

        composable(Routes.CREATE_PROJECT) { backStackEntry ->
            val pickedFolder by backStackEntry.savedStateHandle
                .getStateFlow<String?>("picked_folder", null)
                .collectAsState()
            CreateProjectRoute(
                onBack = { navController.popBackStack() },
                onCreated = { id ->
                    navController.navigate(Routes.editor(id)) { popUpTo(Routes.HUB) }
                },
                onBrowseLocation = { navController.navigate(Routes.FOLDER_PICKER) },
                pickedFolder = pickedFolder,
                onPickedFolderConsumed = { backStackEntry.savedStateHandle["picked_folder"] = null },
            )
        }

        composable(Routes.FOLDER_PICKER) {
            FolderPickerRoute(
                onCancel = { navController.popBackStack() },
                onFolderSelected = { path ->
                    navController.previousBackStackEntry?.savedStateHandle?.set("picked_folder", path)
                    navController.popBackStack()
                },
            )
        }

        composable(Routes.TERMINAL) {
            TerminalRoute(onBack = { navController.popBackStack() })
        }

        composable(Routes.UI_DESIGNER) {
            DesignerRoute(onBack = { navController.popBackStack() })
        }

        composable(Routes.CRASH_REPORT) {
            CrashReportRoute(
                onRestart = { navController.popBackStack() },
                onClose = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.EDITOR_PATTERN,
            arguments = listOf(navArgument("projectId") { }),
        ) { backStackEntry ->
            val projectId = backStackEntry.arguments?.getString("projectId").orEmpty()
            val conflictPath by backStackEntry.savedStateHandle
                .getStateFlow<String?>("git_conflict_path", null).collectAsState()
            EditorRoute(
                projectId = projectId,
                onCloseProject = { navController.popBackStack(Routes.HUB, inclusive = false) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS_ROOT) },
                onOpenAiAgentSettings = { navController.navigate(Routes.SETTINGS_AI_AGENT) },
                onOpenGitDiff = { path, target -> navController.navigate(Routes.gitDiff(projectId, path, target)) },
                onOpenGitHistory = { path -> navController.navigate(Routes.gitHistory(projectId, path)) },
                onOpenGitBlame = { path -> navController.navigate(Routes.gitBlame(projectId, path)) },
                onOpenBranches = { navController.navigate(Routes.gitRefs(projectId, GitRefsMode.BRANCHES.name)) },
                onOpenTags = { navController.navigate(Routes.gitRefs(projectId, GitRefsMode.TAGS.name)) },
                onOpenStashes = { navController.navigate(Routes.gitRefs(projectId, GitRefsMode.STASHES.name)) },
                onOpenHistory = { navController.navigate(Routes.gitHistory(projectId)) },
                onOpenConflicts = { navController.navigate(Routes.gitConflicts(projectId)) },
                openConflictPath = conflictPath,
                onConflictPathOpened = { backStackEntry.savedStateHandle["git_conflict_path"] = null },
            )
        }

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
            GitDiffRoute(projectId, path, target, commitId, onBack = { navController.popBackStack() })
        }

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

        composable(Routes.SETTINGS_ROOT) {
            SettingsRootRoute(
                onBack = { navController.popBackStack() },
                onOpenGeneral = { navController.navigate(Routes.SETTINGS_GENERAL) },
                onOpenEditor = { navController.navigate(Routes.SETTINGS_EDITOR) },
                onOpenAiAgent = { navController.navigate(Routes.SETTINGS_AI_AGENT) },
                onOpenBuildRun = { navController.navigate(Routes.SETTINGS_BUILD_RUN) },
                onOpenServer = { navController.navigate(Routes.SETTINGS_SERVER) },
                onOpenAbout = { navController.navigate(Routes.SETTINGS_ABOUT) },
                onOpenTerminal = { navController.navigate(Routes.TERMINAL) },
                onOpenDeveloperOptions = { navController.navigate(Routes.SETTINGS_DEVELOPER) },
            )
        }
        composable(Routes.SETTINGS_GENERAL) {
            GeneralRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_EDITOR) {
            EditorSettingsRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_AI_AGENT) {
            AiAgentSettingsRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_BUILD_RUN) {
            BuildRunSettingsRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_SERVER) {
            ServerSettingsRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_ABOUT) {
            AboutRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_IDE_CONFIG) {
            IdeConfigRoute(
                onBack = { navController.popBackStack() },
                onOpenServerSettings = { navController.navigate(Routes.SETTINGS_SERVER) },
            )
        }
        composable(Routes.SETTINGS_DEVELOPER) {
            DeveloperOptionsRoute(
                onBack = { navController.popBackStack() },
                onOpenUiDesigner = { navController.navigate(Routes.UI_DESIGNER) },
                onSimulateCrash = { navController.navigate(Routes.CRASH_REPORT) },
                onSimulateAcsMissing = { navController.navigate(Routes.ACS_MISSING) },
                onSimulateUnsupportedDevice = { navController.navigate(Routes.blockingError("device")) },
                onSimulateSdCardInstall = { navController.navigate(Routes.blockingError("sdcard")) },
                onSimulateSecondaryUser = { navController.navigate(Routes.blockingError("user")) },
            )
        }
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
}
