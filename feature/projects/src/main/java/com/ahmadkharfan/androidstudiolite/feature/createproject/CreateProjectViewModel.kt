package com.ahmadkharfan.androidstudiolite.feature.createproject

import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import com.ahmadkharfan.androidstudiolite.domain.model.ProjectNameValidation
import com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage
import com.ahmadkharfan.androidstudiolite.domain.model.validatePackageName
import com.ahmadkharfan.androidstudiolite.domain.model.validateProjectName
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.TemplateRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers

class CreateProjectViewModel(
    private val templateRepository: TemplateRepository,
    private val projectRepository: ProjectRepository,
    private val defaultLocation: String,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseViewModel<CreateProjectUiState, Nothing>(
    initialState = CreateProjectUiState(location = defaultLocation),
    defaultDispatcher = dispatcher,
), CreateProjectInteractionListener {

    private var packageEdited = false

    private var existingProjects: List<Project> = emptyList()

    init {
        tryToExecute(
            block = {
                templateRepository.getTemplates().map {
                    CreateProjectTemplateUiModel(it.id, it.name, it.thumbnail)
                }
            },
            onSuccess = { templates ->
                val default = templates.firstOrNull { it.id == DEFAULT_TEMPLATE_ID }
                    ?: templates.firstOrNull()
                updateState { copy(templates = templates, selectedTemplateId = default?.id) }
            },
        )
        tryToExecute(
            block = { projectRepository.existing() },
            onSuccess = { projects ->
                existingProjects = projects
                if (state.value.projectName == DEFAULT_PROJECT_NAME && !packageEdited) {
                    val suggested = suggestDefaultName(projects)
                    updateState {
                        copy(
                            projectName = suggested,
                            packageName = derivePackage(suggested),
                        )
                    }
                }
                revalidate()
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
        updateState {
            copy(
                projectName = name,
                packageName = if (packageEdited) packageName else derivePackage(name),
            )
        }
        revalidate()
    }

    override fun onPackageChanged(packageName: String) {
        packageEdited = true
        updateState { copy(packageName = packageName) }
        revalidate()
    }

    override fun onLocationChanged(location: String) {
        updateState { copy(location = location) }
    }

    override fun onMinSdkChanged(minSdk: String) {
        updateState { copy(minSdk = minSdk) }
    }

    override fun onLanguageChanged(language: String) {
        updateState { copy(language = language) }
    }

    override fun onCreateProject() {
        startCreate()
    }

    private fun derivePackage(name: String): String {
        val slug = name.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "myapplication" }
        return "com.example.$slug"
    }

    private fun suggestDefaultName(existing: List<Project>): String {
        if (existing.none { it.name.equals(DEFAULT_PROJECT_NAME, ignoreCase = true) }) {
            return DEFAULT_PROJECT_NAME
        }
        var n = 2
        while (existing.any { it.name.equals("$DEFAULT_PROJECT_NAME$n", ignoreCase = true) }) {
            n++
        }
        return "$DEFAULT_PROJECT_NAME$n"
    }

    private fun revalidate() {
        val s = state.value
        val nameError = (validateProjectName(s.projectName) as? ProjectNameValidation.Invalid)?.reason
            ?: "A project named \"${s.projectName}\" already exists"
                .takeIf { existingProjects.any { p -> p.name.equals(s.projectName, ignoreCase = true) } }
        val packageError = (validatePackageName(s.packageName) as? ProjectNameValidation.Invalid)?.reason
            ?: "Another project already uses ${s.packageName}"
                .takeIf { existingProjects.any { p -> p.packageName == s.packageName } }
        updateState { copy(nameError = nameError, packageError = packageError) }
    }

    private fun startCreate() {
        val s = state.value
        if (s.creating || !s.canCreate) return
        tryToExecute(
            onStart = { updateState { copy(creating = true) } },
            block = {
                val templateId = state.value.selectedTemplateId
                    ?: throw IllegalStateException("No template selected")
                projectRepository.createProject(
                    NewProjectSpec(
                        name = s.projectName,
                        packageName = s.packageName,
                        templateId = templateId,
                        language = if (s.language == LANG_JAVA) TemplateLanguage.JAVA else TemplateLanguage.KOTLIN,
                        minSdk = s.minSdk.toIntOrNull() ?: 24,
                        saveLocation = s.location.ifBlank { defaultLocation },
                    ),
                )
            },
            onSuccess = { project ->
                updateState { copy(creating = false, createdProjectId = project.id) }
            },

            onError = { error ->
                updateState {
                    copy(creating = false, nameError = error.message ?: "Couldn't create the project")
                }
            },
        )
    }
}
