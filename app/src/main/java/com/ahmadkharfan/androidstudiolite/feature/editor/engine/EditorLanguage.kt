package com.ahmadkharfan.androidstudiolite.feature.editor.engine
enum class EditorLanguage {
    Kotlin,
    Java,
    Xml,
    Plain;
    companion object {
        fun fromFileName(name: String): EditorLanguage {
            val lower = name.lowercase()
            return when {
                lower.endsWith(".kt") || lower.endsWith(".kts") -> Kotlin
                lower.endsWith(".gradle") -> Kotlin
                lower.endsWith(".java") -> Java
                lower.endsWith(".xml") -> Xml
                else -> Plain
            }
        }
    }
}
