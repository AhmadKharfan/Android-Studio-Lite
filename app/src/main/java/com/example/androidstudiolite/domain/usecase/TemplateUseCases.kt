package com.example.androidstudiolite.domain.usecase

import com.example.androidstudiolite.domain.model.ProjectTemplate
import com.example.androidstudiolite.domain.repository.TemplateRepository

class GetProjectTemplatesUseCase(private val repository: TemplateRepository) {
    suspend operator fun invoke(): List<ProjectTemplate> = repository.getTemplates()
}

sealed interface ProjectNameValidation {
    data object Valid : ProjectNameValidation
    data class Invalid(val reason: String) : ProjectNameValidation
}

/** Pure validation logic — mirrors Android Studio's project-name rules (non-empty, starts with a letter). */
class ValidateProjectNameUseCase {
    operator fun invoke(name: String): ProjectNameValidation = when {
        name.isBlank() -> ProjectNameValidation.Invalid("Project name can't be empty")
        !name.first().isLetter() -> ProjectNameValidation.Invalid("Name must start with a letter")
        name.any { it.isWhitespace() } -> ProjectNameValidation.Invalid("Name can't contain spaces")
        else -> ProjectNameValidation.Valid
    }
}
