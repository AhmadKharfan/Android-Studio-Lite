package com.ahmadkharfan.androidstudiolite.feature.createproject

import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.ProjectBuildDsl
import com.ahmadkharfan.androidstudiolite.domain.model.ProjectNameValidation
import com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage
import com.ahmadkharfan.androidstudiolite.domain.model.validateProjectName
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.TemplateRepository

class CreateProjectViewModel(
    private val templateRepository: TemplateRepository,
    private val projectRepository: ProjectRepository,
) : BaseViewModel<CreateProjectUiState, Nothing>(
    initialState = CreateProjectUiState(),
), CreateProjectInteractionListener {

    init {
        tryToExecute(
            block = {
                templateRepository.getTemplates().map {
                    CreateProjectTemplateUiModel(it.id, it.name, it.description, it.icon, it.tags)
                }
            },
            onSuccess = { templates ->
                updateState { copy(templates = templates, selectedTemplateId = templates.firstOrNull()?.id) }
            },
        )
    }

    override fun onSelectTemplate(id: String) {
        updateState { copy(selectedTemplateId = id) }
    }

    override fun onNextStep() {
        updateState { copy(step = (step + 1).coerceAtMost(2)) }
    }

    override fun onBackStep() {
        updateState { copy(step = (step - 1).coerceAtLeast(0)) }
    }

    override fun onNameChanged(name: String) {
        applyNameChange(name)
    }

    override fun onPackageChanged(packageName: String) {
        updateState { copy(packageName = packageName) }
    }

    override fun onLocationChanged(location: String) {
        updateState { copy(location = location) }
    }

    override fun onMinSdkChanged(minSdk: String) {
        updateState { copy(minSdk = minSdk) }
    }

    override fun onTargetSdkChanged(targetSdk: String) {
        updateState { copy(targetSdk = targetSdk) }
    }

    override fun onLanguageChanged(language: String) {
        updateState { copy(language = language) }
    }

    override fun onBuildDslChanged(dsl: String) {
        updateState { copy(buildDsl = dsl) }
    }

    override fun onToggleCpp(enabled: Boolean) {
        updateState { copy(useCpp = enabled) }
    }

    override fun onCreateProject() {
        startCreate()
    }

    private fun applyNameChange(name: String) {
        val validation = validateProjectName(name)
        val slug = name.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "myapplication" }
        updateState {
            copy(
                projectName = name,
                packageName = "com.example.$slug",
                location = "~/projects/$name",
                nameError = (validation as? ProjectNameValidation.Invalid)?.reason,
            )
        }
    }

    private fun startCreate() {
        if (state.value.creating) return
        updateState { copy(creating = true) }
        tryToExecute(
            block = {
                val s = state.value
                val templateId = s.selectedTemplateId
                    ?: throw IllegalStateException("No template selected")
                projectRepository.createProject(
                    NewProjectSpec(
                        name = s.projectName,
                        packageName = s.packageName,
                        templateId = templateId,
                        language = if (s.language == LANG_JAVA) TemplateLanguage.JAVA else TemplateLanguage.KOTLIN,
                        buildDsl = if (s.buildDsl == DSL_GROOVY) ProjectBuildDsl.GROOVY else ProjectBuildDsl.KTS,
                        minSdk = s.minSdk.toIntOrNull() ?: 24,
                        targetSdk = s.targetSdk.toIntOrNull() ?: 34,
                        useCpp = s.useCpp,
                    ),
                )
            },
            onSuccess = { project ->
                updateState { copy(createdProjectId = project.id) }
            },
        )
    }
}
