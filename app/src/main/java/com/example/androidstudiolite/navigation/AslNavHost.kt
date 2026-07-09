package com.example.androidstudiolite.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.androidstudiolite.feature.acsmissing.AcsMissingRoute
import com.example.androidstudiolite.feature.blockingerror.BlockingErrorRoute
import com.example.androidstudiolite.feature.blockingerror.BlockingErrorType
import com.example.androidstudiolite.feature.createproject.CreateProjectRoute
import com.example.androidstudiolite.feature.crashreport.CrashReportRoute
import com.example.androidstudiolite.feature.editor.EditorRoute
import com.example.androidstudiolite.feature.folderpicker.FolderPickerRoute
import com.example.androidstudiolite.feature.hub.HubRoute
import com.example.androidstudiolite.feature.onboarding.complete.CompleteRoute
import com.example.androidstudiolite.feature.onboarding.permissions.PermissionsRoute
import com.example.androidstudiolite.feature.onboarding.setup.SetupRoute
import com.example.androidstudiolite.feature.onboarding.statistics.StatisticsRoute
import com.example.androidstudiolite.feature.onboarding.welcome.WelcomeRoute
import com.example.androidstudiolite.feature.settings.about.AboutRoute
import com.example.androidstudiolite.feature.settings.aiagent.AiAgentSettingsRoute
import com.example.androidstudiolite.feature.settings.buildrun.BuildRunSettingsRoute
import com.example.androidstudiolite.feature.settings.developer.DeveloperOptionsRoute
import com.example.androidstudiolite.feature.settings.editor.EditorSettingsRoute
import com.example.androidstudiolite.feature.settings.general.GeneralRoute
import com.example.androidstudiolite.feature.settings.ideconfig.IdeConfigRoute
import com.example.androidstudiolite.feature.settings.root.SettingsRootRoute
import com.example.androidstudiolite.feature.terminal.TerminalRoute
import com.example.androidstudiolite.feature.uidesigner.DesignerRoute

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
            WelcomeRoute(onGetStarted = { navController.navigate(Routes.ONBOARDING_STATISTICS) })
        }
        composable(Routes.ONBOARDING_STATISTICS) {
            StatisticsRoute(
                onContinue = { navController.navigate(Routes.ONBOARDING_PERMISSIONS) },
                onSkip = { navController.navigate(Routes.ONBOARDING_PERMISSIONS) },
            )
        }
        composable(Routes.ONBOARDING_PERMISSIONS) {
            PermissionsRoute(onContinue = { navController.navigate(Routes.ONBOARDING_SETUP) })
        }
        composable(Routes.ONBOARDING_SETUP) {
            SetupRoute(
                onContinue = { navController.navigate(Routes.ONBOARDING_COMPLETE) },
                onSkip = { navController.navigate(Routes.ONBOARDING_COMPLETE) },
            )
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
            EditorRoute(
                projectId = projectId,
                onCloseProject = { navController.popBackStack(Routes.HUB, inclusive = false) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS_ROOT) },
                onOpenAiAgentSettings = { navController.navigate(Routes.SETTINGS_AI_AGENT) },
            )
        }

        composable(Routes.SETTINGS_ROOT) {
            SettingsRootRoute(
                onBack = { navController.popBackStack() },
                onOpenGeneral = { navController.navigate(Routes.SETTINGS_GENERAL) },
                onOpenEditor = { navController.navigate(Routes.SETTINGS_EDITOR) },
                onOpenAiAgent = { navController.navigate(Routes.SETTINGS_AI_AGENT) },
                onOpenBuildRun = { navController.navigate(Routes.SETTINGS_BUILD_RUN) },
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
        composable(Routes.SETTINGS_ABOUT) {
            AboutRoute(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS_IDE_CONFIG) {
            IdeConfigRoute(onBack = { navController.popBackStack() })
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
