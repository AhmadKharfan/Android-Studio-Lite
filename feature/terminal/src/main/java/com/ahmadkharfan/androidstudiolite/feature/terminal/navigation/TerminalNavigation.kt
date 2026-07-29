package com.ahmadkharfan.androidstudiolite.feature.terminal.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.ahmadkharfan.androidstudiolite.feature.terminal.TerminalRoute

fun NavGraphBuilder.terminalGraph(onBack: () -> Unit) {
    composable(TerminalRoutes.TERMINAL) { TerminalRoute(onBack = onBack) }
}
