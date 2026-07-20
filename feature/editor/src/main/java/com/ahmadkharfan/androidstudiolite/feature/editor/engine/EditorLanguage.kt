package com.ahmadkharfan.androidstudiolite.feature.editor.engine
enum class EditorLanguage {
    Kotlin,
    Java,
    Xml,
    Plain;

    val displayName: String
        get() = when (this) {
            Kotlin -> "Kotlin"
            Java -> "Java"
            Xml -> "XML"
            Plain -> "Plain text"
        }

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
