package com.ahmadkharfan.androidstudiolite.designsystem.component.ide

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme

/**
 * Screen scaffold for the app.
 *
 * Exists so features do not import Material directly: the container colour comes from the design
 * system's own tokens rather than Material's colour scheme, which is what keeps a screen looking
 * right when the theme changes.
 */
@Composable
fun AslScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    snackbarHost: @Composable () -> Unit = {},
    containerColor: Color = AslTheme.colors.surface,
    contentColor: Color = AslTheme.colors.textPrimary,
    contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = modifier,
        topBar = topBar,
        bottomBar = bottomBar,
        floatingActionButton = floatingActionButton,
        snackbarHost = snackbarHost,
        containerColor = containerColor,
        contentColor = contentColor,
        contentWindowInsets = contentWindowInsets,
        content = content,
    )
}

/** Host for [AslSnackbarState]; pass to [AslScaffold]'s `snackbarHost`. */
@Composable
fun AslSnackbarHost(hostState: AslSnackbarState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState = hostState.delegate, modifier = modifier)
}

/**
 * Snackbar state a feature can hold without touching Material.
 *
 * A typealias would not do: it still resolves to Material's `SnackbarHostState`, which puts Material
 * back on every caller's classpath. Wrapping keeps the dependency inside the design system.
 */
@Stable
class AslSnackbarState {
    internal val delegate: SnackbarHostState = SnackbarHostState()

    suspend fun showSnackbar(message: String, actionLabel: String? = null): Boolean =
        delegate.showSnackbar(message = message, actionLabel = actionLabel) == SnackbarResult.ActionPerformed
}

/** Remembers an [AslSnackbarState] across recompositions. */
@Composable
fun rememberAslSnackbarState(): AslSnackbarState = remember { AslSnackbarState() }
