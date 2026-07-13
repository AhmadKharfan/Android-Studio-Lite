package com.ahmadkharfan.androidstudiolite.feature.createproject
import androidx.compose.runtime.Immutable

@Immutable
data class CreateProjectTemplateUiModel(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val tags: List<String>,
)

@Immutable
data class SdkOption(val value: String, val label: String)

val MIN_SDK_OPTIONS = listOf(
    SdkOption("24", "API 24 — Android 7.0"),
    SdkOption("26", "API 26 — Android 8.0"),
    SdkOption("30", "API 30 — Android 11"),
)

// Capped at the compat-matrix compileSdk (34); higher targets aren't supported by the pinned AGP.
val TARGET_SDK_OPTIONS = listOf(
    SdkOption("30", "API 30 — Android 11"),
    SdkOption("33", "API 33 — Android 13"),
    SdkOption("34", "API 34 — Android 14"),
)

const val LANG_KOTLIN = "kotlin"
const val LANG_JAVA = "java"
const val DSL_KTS = "kts"
const val DSL_GROOVY = "groovy"

@Immutable
data class CreateProjectUiState(
    val step: Int = 0,
    val templates: List<CreateProjectTemplateUiModel> = emptyList(),
    val selectedTemplateId: String? = null,
    val projectName: String = "MyApplication",
    val packageName: String = "com.example.myapplication",
    val location: String = "~/projects/MyApplication",
    val minSdk: String = "26",
    val targetSdk: String = "34",
    val language: String = LANG_KOTLIN,
    val buildDsl: String = DSL_KTS,
    val useCpp: Boolean = false,
    val nameError: String? = null,
    val creating: Boolean = false,
    val createdProjectId: String? = null,
) {
    val selectedTemplate: CreateProjectTemplateUiModel?
        get() = templates.firstOrNull { it.id == selectedTemplateId }

    val minSdkLabel: String
        get() = MIN_SDK_OPTIONS.firstOrNull { it.value == minSdk }?.label ?: minSdk

    val targetSdkLabel: String
        get() = TARGET_SDK_OPTIONS.firstOrNull { it.value == targetSdk }?.label ?: targetSdk

    val languageLabel: String
        get() = if (language == LANG_JAVA) "Java" else "Kotlin"

    val buildDslLabel: String
        get() = if (buildDsl == DSL_GROOVY) "Groovy" else "Kotlin (KTS)"

    val canGoNext: Boolean
        get() = when (step) {
            0 -> selectedTemplateId != null
            1 -> nameError == null && projectName.isNotBlank()
            else -> true
        }
}
