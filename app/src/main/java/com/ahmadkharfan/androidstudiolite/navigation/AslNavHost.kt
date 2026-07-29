package com.ahmadkharfan.androidstudiolite.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController

@Composable
fun AslNavHost(
    startDestination: String,
    openProjectId: String? = null,
    onOpenProjectConsumed: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    val currentOnOpenProjectConsumed by rememberUpdatedState(onOpenProjectConsumed)
    LaunchedEffect(openProjectId) {
        val id = openProjectId ?: return@LaunchedEffect

        if (startDestination != Routes.editor(id)) {
            navController.navigate(Routes.editor(id)) {
                launchSingleTop = true
            }
        }
        currentOnOpenProjectConsumed()
    }
    NavHost(
        navController = navController,
        startDestination = startDestination,
        enterTransition = { aslEnter() },
        exitTransition = { aslExit() },
        popEnterTransition = { aslPopEnter() },
        popExitTransition = { aslPopExit() },
    ) {
        onboardingGraph(navController)
        projectsGraph(navController)
        utilityGraph(navController)
        editorGraph(navController)
        settingsGraph(navController)
        deviceSupportGraph()
    }
}
