package com.ahmadkharfan.androidstudiolite.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier

/** M3-aligned subset of the token set — everything else (editor/syntax/terminal/semantic
 *  warning-info-success) lives only in [AslColorScheme] since M3 has no slot for it. */
private fun colorScheme(colors: AslColorScheme, dark: Boolean): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        background = colors.bgBase,
        surface = colors.bgBase,
        surfaceContainerLowest = colors.bgSunken,
        surfaceContainerLow = colors.bgElevated,
        surfaceContainer = colors.surface,
        surfaceContainerHigh = colors.surfaceContainerHigh,
        surfaceContainerHighest = colors.surfaceContainerHigh,
        onBackground = colors.textPrimary,
        onSurface = colors.textPrimary,
        onSurfaceVariant = colors.textSecondary,
        outline = colors.textTertiary,
        outlineVariant = colors.borderDefault,
        primary = colors.accentPrimary,
        primaryContainer = colors.accentPrimaryContainer,
        onPrimary = colors.accentOnPrimary,
        onPrimaryContainer = colors.accentOnPrimary,
        error = colors.error,
        errorContainer = colors.errorContainer,
        onError = if (dark) colors.textPrimary else colors.bgBase,
        onErrorContainer = colors.error,
    )
}

/** Namespaced accessors mirroring the design's own token vocabulary, alongside [MaterialTheme]. */
object AslTheme {
    val colors: AslColorScheme
        @Composable get() = LocalAslColors.current

    val code: AslCodeTypography
        @Composable get() = AslCode
}

/**
 * Root theme. Dark is the primary theme for this product; it follows the system setting by
 * default but callers (e.g. a settings screen) can force either mode via [darkTheme].
 */
@Composable
fun AslAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    accentId: String = "emerald",
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) AslDarkColors else AslLightColors
    val aslColors = base.withAccent(accentColorsFor(accentId, darkTheme))
    CompositionLocalProvider(LocalAslColors provides aslColors) {
        MaterialTheme(
            colorScheme = colorScheme(aslColors, darkTheme),
            typography = AslTypography,
            shapes = AslShapes,
        ) {
            AslSystemBarStyle(darkTheme = darkTheme)
            // Paints the themed background behind the whole app *before* any screen renders, so
            // in-flight nav transitions (sliding/fading between screens) never expose the raw
            // (always-light) Android window background — without this, that flash reads as a
            // stray light "ripple" through the content, worst in dark mode.
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background,
            ) {
                content()
            }
        }
    }
}
