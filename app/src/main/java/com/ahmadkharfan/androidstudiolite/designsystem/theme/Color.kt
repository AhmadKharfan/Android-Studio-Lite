package com.ahmadkharfan.androidstudiolite.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Full Android Studio Lite color token set (tokens/colors.css). Only the subset with a direct
 * M3 slot (accent, surfaces, text, error) is also mapped onto [androidx.compose.material3.ColorScheme]
 * in Theme.kt — editor/syntax/terminal/warning/info/success tokens have no M3 equivalent and are
 * only reachable through [AslTheme.colors].
 */
data class AslColorScheme(
    val bgBase: Color,
    val bgElevated: Color,
    val bgSunken: Color,

    val surface: Color,
    val surfaceContainerLow: Color,
    val surfaceContainerHigh: Color,

    val editorCanvas: Color,
    val editorGutter: Color,
    val editorSelection: Color,
    val editorCursor: Color,
    val editorLineHighlight: Color,

    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,

    val borderSubtle: Color,
    val borderDefault: Color,
    val borderStrong: Color,

    val accentPrimary: Color,
    val accentPrimaryContainer: Color,
    val accentOnPrimary: Color,
    val accentHover: Color,
    val accentPressed: Color,

    val error: Color,
    val errorContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val success: Color,
    val successContainer: Color,

    val syntaxKeyword: Color,
    val syntaxString: Color,
    val syntaxComment: Color,
    val syntaxFunction: Color,
    val syntaxType: Color,
    val syntaxVariable: Color,
    val syntaxNumber: Color,

    val terminalBg: Color,
    val terminalStdout: Color,
    val terminalStderr: Color,
    val terminalPrompt: Color,
)

val AslLightColors = AslColorScheme(
    bgBase = Color(0xFFF7F8FA),
    bgElevated = Color(0xFFFFFFFF),
    bgSunken = Color(0xFFEFF0F3),

    surface = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3F5),
    surfaceContainerHigh = Color(0xFFE9EAEE),

    editorCanvas = Color(0xFFFFFFFF),
    editorGutter = Color(0xFFF7F8FA),
    editorSelection = Color(0xFFD7EFE6),
    editorCursor = Color(0xFF10B981),
    editorLineHighlight = Color(0xFFF2F6F4),

    textPrimary = Color(0xFF1E1E2E),
    textSecondary = Color(0xFF5F6371),
    textTertiary = Color(0xFF9A9DAB),
    textDisabled = Color(0xFFBCBEC9),

    borderSubtle = Color(0xFFECEDF1),
    borderDefault = Color(0xFFE4E5E9),
    borderStrong = Color(0xFFC8CAD3),

    accentPrimary = Color(0xFF10B981),
    accentPrimaryContainer = Color(0xFFD9F3EA),
    accentOnPrimary = Color(0xFFFFFFFF),
    accentHover = Color(0xFF0EA774),
    accentPressed = Color(0xFF0C9569),

    error = Color(0xFFEF4444),
    errorContainer = Color(0xFFFDEAEA),
    warning = Color(0xFFF59E0B),
    warningContainer = Color(0xFFFDF2DD),
    info = Color(0xFF3B82F6),
    infoContainer = Color(0xFFE5EEFD),
    success = Color(0xFF10B981),
    successContainer = Color(0xFFD9F3EA),

    syntaxKeyword = Color(0xFFCF222E),
    syntaxString = Color(0xFF0A3069),
    syntaxComment = Color(0xFF6E7781),
    syntaxFunction = Color(0xFF8250DF),
    syntaxType = Color(0xFF953800),
    syntaxVariable = Color(0xFF24292F),
    syntaxNumber = Color(0xFF0550AE),

    terminalBg = Color(0xFF1E1E1E),
    terminalStdout = Color(0xFFD4D4D4),
    terminalStderr = Color(0xFFF87171),
    terminalPrompt = Color(0xFF34D399),
)

val AslDarkColors = AslColorScheme(
    bgBase = Color(0xFF1E1E2E),
    bgElevated = Color(0xFF2B2D30),
    bgSunken = Color(0xFF17171F),

    surface = Color(0xFF2B2D30),
    surfaceContainerLow = Color(0xFF26282C),
    surfaceContainerHigh = Color(0xFF35373C),

    editorCanvas = Color(0xFF1E1E1E),
    editorGutter = Color(0xFF1E1E1E),
    editorSelection = Color(0xFF1E3A31),
    editorCursor = Color(0xFF34D399),
    editorLineHighlight = Color(0xFF26282A),

    textPrimary = Color(0xFFDFE1E5),
    textSecondary = Color(0xFF8B8FA3),
    textTertiary = Color(0xFF6C7086),
    textDisabled = Color(0xFF4E5058),

    borderSubtle = Color(0xFF2E3035),
    borderDefault = Color(0xFF393B40),
    borderStrong = Color(0xFF4A4D55),

    accentPrimary = Color(0xFF34D399),
    accentPrimaryContainer = Color(0xFF0E3B2E),
    accentOnPrimary = Color(0xFF08281E),
    accentHover = Color(0xFF4ADCA6),
    accentPressed = Color(0xFF2BBD88),

    error = Color(0xFFF87171),
    errorContainer = Color(0xFF3D2427),
    warning = Color(0xFFFBBF24),
    warningContainer = Color(0xFF3D3323),
    info = Color(0xFF60A5FA),
    infoContainer = Color(0xFF1E2A45),
    success = Color(0xFF34D399),
    successContainer = Color(0xFF0E3B2E),

    syntaxKeyword = Color(0xFFCC7832),
    syntaxString = Color(0xFF6A8759),
    syntaxComment = Color(0xFF808080),
    syntaxFunction = Color(0xFFFFC66D),
    syntaxType = Color(0xFF4EC9B0),
    syntaxVariable = Color(0xFFA9B7C6),
    syntaxNumber = Color(0xFF6897BB),

    terminalBg = Color(0xFF17171F),
    terminalStdout = Color(0xFFD4D4D4),
    terminalStderr = Color(0xFFF87171),
    terminalPrompt = Color(0xFF34D399),
)

val LocalAslColors = staticCompositionLocalOf { AslDarkColors }
