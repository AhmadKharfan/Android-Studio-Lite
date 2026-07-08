package com.example.androidstudiolite.feature.createproject.uiState

data class CreateProjectTemplateUiModel(
    val id: String,
    val name: String,
    val description: String,
    val icon: String,
    val tags: List<String>,
)

data class MinSdkOption(val value: String, val label: String)

val MIN_SDK_OPTIONS = listOf(
    MinSdkOption("24", "API 24 — Android 7.0"),
    MinSdkOption("26", "API 26 — Android 8.0"),
    MinSdkOption("30", "API 30 — Android 11"),
)

data class CreateProjectUiState(
    val step: Int = 0,
    val templates: List<CreateProjectTemplateUiModel> = emptyList(),
    val selectedTemplateId: String? = null,
    val projectName: String = "MyApplication",
    val packageName: String = "com.example.myapplication",
    val location: String = "~/projects/MyApplication",
    val minSdk: String = "26",
    val nameError: String? = null,
    val creating: Boolean = false,
    val createdProjectId: String? = null,
) {
    val selectedTemplate: CreateProjectTemplateUiModel?
        get() = templates.firstOrNull { it.id == selectedTemplateId }

    val minSdkLabel: String
        get() = MIN_SDK_OPTIONS.firstOrNull { it.value == minSdk }?.label ?: minSdk

    val canGoNext: Boolean
        get() = when (step) {
            0 -> selectedTemplateId != null
            1 -> nameError == null && projectName.isNotBlank()
            else -> true
        }
}
