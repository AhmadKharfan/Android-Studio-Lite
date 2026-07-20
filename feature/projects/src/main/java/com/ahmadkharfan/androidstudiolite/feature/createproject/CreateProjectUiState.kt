package com.ahmadkharfan.androidstudiolite.feature.createproject
import androidx.compose.runtime.Immutable

@Immutable
data class CreateProjectTemplateUiModel(
    val id: String,
    val name: String,
    val thumbnail: String,
)

@Immutable
data class SdkOption(val value: String, val label: String)

val MIN_SDK_OPTIONS = listOf(
    SdkOption("24", "API 24 — Android 7.0"),
    SdkOption("26", "API 26 — Android 8.0"),
    SdkOption("30", "API 30 — Android 11"),
)

const val LANG_KOTLIN = "kotlin"
const val LANG_JAVA = "java"

const val DEFAULT_TEMPLATE_ID = "empty-compose"

@Immutable
data class CreateProjectUiState(
    val step: Int = 0,
    val templates: List<CreateProjectTemplateUiModel> = emptyList(),
    val selectedTemplateId: String? = null,
    val projectName: String = "MyApplication",
    val packageName: String = "com.example.myapplication",
    val location: String = "",
    val minSdk: String = "26",
    val language: String = LANG_KOTLIN,
    val nameError: String? = null,
    val packageError: String? = null,
    val creating: Boolean = false,
    val createdProjectId: String? = null,
) {
    val selectedTemplate: CreateProjectTemplateUiModel?
        get() = templates.firstOrNull { it.id == selectedTemplateId }

    val minSdkLabel: String
        get() = MIN_SDK_OPTIONS.firstOrNull { it.value == minSdk }?.label ?: minSdk

    val languageLabel: String
        get() = if (language == LANG_JAVA) "Java" else "Kotlin"

    val canCreate: Boolean
        get() = selectedTemplateId != null && nameError == null && packageError == null &&
            projectName.isNotBlank() && packageName.isNotBlank()

    val canGoNext: Boolean
        get() = if (step == 0) selectedTemplateId != null else canCreate
}
