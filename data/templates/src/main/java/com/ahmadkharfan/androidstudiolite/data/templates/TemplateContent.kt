package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec

object TemplateContent {

    fun pascal(spec: NewProjectSpec): String {
        val cleaned = spec.name.filter { it.isLetterOrDigit() || it == ' ' }
        val camel = cleaned.split(' ').filter { it.isNotBlank() }
            .joinToString("") { it.replaceFirstChar(Char::uppercase) }
        val safe = camel.ifBlank { "App" }
        return if (safe.first().isLetter()) safe else "App$safe"
    }

    fun themeName(spec: NewProjectSpec): String = "Theme.${pascal(spec)}"

    fun manifest(
        spec: NewProjectSpec,
        activityName: String?,
        launcher: Boolean = true,
        themeAttr: String = "@style/${themeName(spec)}",
    ): String = buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        appendLine("<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\">")
        appendLine()
        appendLine("    <application")
        appendLine("        android:allowBackup=\"true\"")
        appendLine("        android:icon=\"@mipmap/ic_launcher\"")
        appendLine("        android:label=\"@string/app_name\"")
        appendLine("        android:supportsRtl=\"true\"")
        appendLine("        android:theme=\"$themeAttr\">")
        if (activityName != null) {
            appendLine("        <activity")
            appendLine("            android:name=\".$activityName\"")
            appendLine("            android:exported=\"true\">")
            if (launcher) {
                appendLine("            <intent-filter>")
                appendLine("                <action android:name=\"android.intent.action.MAIN\" />")
                appendLine("                <category android:name=\"android.intent.category.LAUNCHER\" />")
                appendLine("            </intent-filter>")
            }
            appendLine("        </activity>")
        }
        appendLine("    </application>")
        appendLine()
        append("</manifest>")
    }

    fun stringsXml(spec: NewProjectSpec, extra: Map<String, String> = emptyMap()): String = buildString {
        appendLine("<resources>")
        appendLine("    <string name=\"app_name\">${spec.name}</string>")
        for ((k, v) in extra) appendLine("    <string name=\"$k\">$v</string>")
        append("</resources>")
    }

    fun viewsThemesXml(spec: NewProjectSpec): String =
        """
        <resources xmlns:tools="http://schemas.android.com/tools">
            <style name="${themeName(spec)}" parent="Theme.Material3.DayNight.NoActionBar" />
        </resources>
        """.trimIndent()

    fun composeThemesXml(spec: NewProjectSpec): String =
        """
        <resources>
            <style name="${themeName(spec)}" parent="android:Theme.Material.Light.NoActionBar" />
        </resources>
        """.trimIndent()


    fun composeThemeFiles(recipe: ProjectRecipe, spec: NewProjectSpec) {
        val name = pascal(spec)
        recipe.sourceFileIn(
            "ui.theme", "Color.kt",
            """
            package ${spec.packageName}.ui.theme

            import androidx.compose.ui.graphics.Color

            val Purple80 = Color(0xFFD0BCFF)
            val PurpleGrey80 = Color(0xFFCCC2DC)
            val Pink80 = Color(0xFFEFB8C8)

            val Purple40 = Color(0xFF6650A4)
            val PurpleGrey40 = Color(0xFF625B71)
            val Pink40 = Color(0xFF7D5260)
            """.trimIndent(),
        )
        recipe.sourceFileIn(
            "ui.theme", "Type.kt",
            """
            package ${spec.packageName}.ui.theme

            import androidx.compose.material3.Typography
            import androidx.compose.ui.text.TextStyle
            import androidx.compose.ui.text.font.FontFamily
            import androidx.compose.ui.text.font.FontWeight
            import androidx.compose.ui.unit.sp

            val Typography = Typography(
                bodyLarge = TextStyle(
                    fontFamily = FontFamily.Default,
                    fontWeight = FontWeight.Normal,
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    letterSpacing = 0.5.sp,
                ),
            )
            """.trimIndent(),
        )
        recipe.sourceFileIn(
            "ui.theme", "Theme.kt",
            """
            package ${spec.packageName}.ui.theme

            import android.app.Activity
            import androidx.compose.foundation.isSystemInDarkTheme
            import androidx.compose.material3.MaterialTheme
            import androidx.compose.material3.darkColorScheme
            import androidx.compose.material3.lightColorScheme
            import androidx.compose.runtime.Composable

            private val DarkColorScheme = darkColorScheme(
                primary = Purple80,
                secondary = PurpleGrey80,
                tertiary = Pink80,
            )

            private val LightColorScheme = lightColorScheme(
                primary = Purple40,
                secondary = PurpleGrey40,
                tertiary = Pink40,
            )

            @Composable
            fun ${name}Theme(
                darkTheme: Boolean = isSystemInDarkTheme(),
                content: @Composable () -> Unit,
            ) {
                val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
                MaterialTheme(
                    colorScheme = colorScheme,
                    typography = Typography,
                    content = content,
                )
            }
            """.trimIndent(),
        )
    }

    fun addStandardTestDeps(recipe: ProjectRecipe) {
        recipe.testImplementation(Catalog.junit)
        recipe.androidTestImplementation(Catalog.androidxJunit)
        recipe.androidTestImplementation(Catalog.espressoCore)
    }
}
