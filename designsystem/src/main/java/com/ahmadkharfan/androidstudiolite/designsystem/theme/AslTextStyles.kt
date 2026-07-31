package com.ahmadkharfan.androidstudiolite.designsystem.theme

import androidx.compose.ui.text.TextStyle

/**
 * The type scale, exposed as plain [TextStyle] values.
 *
 * `AslTypography` is a Material `Typography`, so reading a style off it forces every caller onto
 * Material's classpath. These properties hand back `androidx.compose.ui.text.TextStyle` instead,
 * which is a Compose UI type — features get the same styles without depending on Material.
 */
object AslTextStyles {
    val displayLarge: TextStyle get() = AslTypography.displayLarge
    val displayMedium: TextStyle get() = AslTypography.displayMedium
    val displaySmall: TextStyle get() = AslTypography.displaySmall
    val headlineLarge: TextStyle get() = AslTypography.headlineLarge
    val headlineMedium: TextStyle get() = AslTypography.headlineMedium
    val headlineSmall: TextStyle get() = AslTypography.headlineSmall
    val titleLarge: TextStyle get() = AslTypography.titleLarge
    val titleMedium: TextStyle get() = AslTypography.titleMedium
    val titleSmall: TextStyle get() = AslTypography.titleSmall
    val bodyLarge: TextStyle get() = AslTypography.bodyLarge
    val bodyMedium: TextStyle get() = AslTypography.bodyMedium
    val bodySmall: TextStyle get() = AslTypography.bodySmall
    val labelLarge: TextStyle get() = AslTypography.labelLarge
    val labelMedium: TextStyle get() = AslTypography.labelMedium
    val labelSmall: TextStyle get() = AslTypography.labelSmall
}
