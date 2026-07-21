package com.ahmadkharfan.androidstudiolite.designsystem.icon

import androidx.compose.ui.graphics.Color
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslColorScheme

object AslFileIcons {

    fun iconFor(fileName: String): String {
        val lower = fileName.lowercase()
        val ext = extensionOf(lower)
        return when {
            isGradleFile(lower) -> "file-cog"
            lower == "androidmanifest.xml" -> "smartphone"
            lower == "dockerfile" || lower.startsWith("dockerfile.") -> "box"
            lower == "license" || lower.startsWith("license.") -> "scroll-text"
            lower == ".gitignore" || lower == ".gitattributes" || lower == ".gitmodules" -> "git-branch"
            lower == "proguard-rules.pro" || lower.endsWith(".pro") -> "shield"
            else -> when (ext) {
                "kt", "kts" -> "braces"
                "java" -> "coffee"
                "xml" -> "code"
                "json", "jsonc" -> "braces"
                "md", "markdown", "mdx" -> "book-open"
                "txt", "log", "rst" -> "file-text"
                "properties", "toml", "ini", "cfg", "conf" -> "settings-2"
                "yml", "yaml" -> "file-text"
                "html", "htm", "xhtml" -> "globe"
                "css", "scss", "sass", "less" -> "palette"
                "js", "jsx", "mjs", "cjs", "ts", "tsx" -> "code"
                "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico" -> "image"
                "svg", "vector" -> "shapes"
                "ttf", "otf", "ttc", "woff", "woff2" -> "type"
                "apk", "aab", "aar", "jar", "zip", "tar", "gz", "tgz", "7z" -> "package"
                "so", "dylib", "dll" -> "cpu"
                "sh", "bash", "zsh", "bat", "cmd", "ps1" -> "terminal"
                "sql", "db", "sqlite" -> "database"
                "proto" -> "api"
                "gradle" -> "file-cog"
                "mp3", "wav", "ogg", "flac", "aac", "m4a" -> "audio-file"
                "mp4", "webm", "mkv", "avi", "mov" -> "video-file"
                "pdf" -> "file-text"
                "key", "jks", "keystore", "p12", "pem", "crt" -> "key"
                else -> "file"
            }
        }
    }

    fun tintFor(fileName: String, colors: AslColorScheme): Color {
        val lower = fileName.lowercase()
        val ext = extensionOf(lower)
        return when {
            isGradleFile(lower) -> colors.warning
            lower == "androidmanifest.xml" -> colors.success
            lower == ".gitignore" || lower == ".gitattributes" || lower == ".gitmodules" -> Color(0xFFF05133)
            lower.endsWith(".pro") -> colors.info
            else -> when (ext) {
                "kt", "kts" -> Color(0xFF7F52FF)
                "java" -> Color(0xFFE76F00)
                "xml" -> colors.syntaxString
                "json", "jsonc" -> colors.syntaxNumber
                "md", "markdown", "mdx" -> colors.info
                "properties", "toml", "ini", "cfg", "conf" -> colors.textSecondary
                "yml", "yaml" -> colors.syntaxKeyword
                "html", "htm", "xhtml" -> Color(0xFFE44D26)
                "css", "scss", "sass", "less" -> Color(0xFF2965F1)
                "js", "jsx", "mjs", "cjs" -> Color(0xFFF7DF1E)
                "ts", "tsx" -> Color(0xFF3178C6)
                "png", "jpg", "jpeg", "gif", "webp", "bmp", "ico", "svg", "vector" -> colors.success
                "ttf", "otf", "ttc", "woff", "woff2" -> colors.syntaxFunction
                "apk", "aab", "aar", "jar", "zip", "tar", "gz", "tgz", "7z", "so" -> colors.warning
                "sh", "bash", "zsh", "bat", "cmd", "ps1" -> colors.success
                "sql", "db", "sqlite" -> colors.info
                "proto" -> colors.accentPrimary
                "gradle" -> colors.warning
                "mp3", "wav", "ogg", "flac", "aac", "m4a", "mp4", "webm", "mkv", "avi", "mov" -> colors.syntaxVariable
                "key", "jks", "keystore", "p12", "pem", "crt" -> colors.error
                else -> colors.textTertiary
            }
        }
    }

    private fun isGradleFile(lower: String): Boolean =
        lower == "gradlew" ||
            lower == "gradlew.bat" ||
            lower.endsWith(".gradle") ||
            lower.endsWith(".gradle.kts") ||
            lower == "gradle.properties" ||
            lower == "gradle-wrapper.properties" ||
            lower == "settings.gradle" ||
            lower == "settings.gradle.kts" ||
            lower == "build.gradle" ||
            lower == "build.gradle.kts" ||
            lower == "libs.versions.toml"

    private fun extensionOf(lowerName: String): String {
        val dot = lowerName.lastIndexOf('.')
        if (dot <= 0 || dot == lowerName.lastIndex) return ""
        return lowerName.substring(dot + 1)
    }
}
