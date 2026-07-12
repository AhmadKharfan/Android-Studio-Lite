package com.ahmadkharfan.androidstudiolite.domain.model

sealed interface ProjectNameValidation {
    data object Valid : ProjectNameValidation
    data class Invalid(val reason: String) : ProjectNameValidation
}

/** Mirrors Android Studio's project-name rules (non-empty, starts with a letter). */
fun validateProjectName(name: String): ProjectNameValidation = when {
    name.isBlank() -> ProjectNameValidation.Invalid("Project name can't be empty")
    !name.first().isLetter() -> ProjectNameValidation.Invalid("Name must start with a letter")
    name.any { it.isWhitespace() } -> ProjectNameValidation.Invalid("Name can't contain spaces")
    else -> ProjectNameValidation.Valid
}
