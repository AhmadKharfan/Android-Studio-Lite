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

/**
 * Drives the create-project wizard: template → configure (name, package, save location, language,
 * minimum SDK) → summary.
 *
 * The option set mirrors Android Studio's dialog. Everything else the generated project needs
 * (compileSdk/targetSdk, KTS, the Gradle version) is pinned in the template layer rather than asked,
 * because those were cosmetic choices the remote build server doesn't honor per-project.
 *
 * @param defaultLocation absolute on-device projects root; the default parent for new projects.
 */
class CreateProjectViewModel(
    private val templateRepository: TemplateRepository,
    private val projectRepository: ProjectRepository,
    private val defaultLocation: String,
    dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : BaseViewModel<CreateProjectUiState, Nothing>(
    initialState = CreateProjectUiState(location = defaultLocation),
    defaultDispatcher = dispatcher,
), CreateProjectInteractionListener {

    /**
     * Whether the user has typed in the package field. Until they do, the package tracks the project
     * name; afterwards their edit is left alone (the old code re-derived it on every name keystroke,
     * silently discarding what they'd typed).
     */
    private var packageEdited = false

    /** Snapshot of registered projects, for the duplicate name/package check. */
    private var existingProjects: List<Project> = emptyList()

    init {
        tryToExecute(
            block = {
                templateRepository.getTemplates().map {
                    CreateProjectTemplateUiModel(it.id, it.name, it.description, it.icon, it.tags)
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
                // The defaults ("MyApplication") can themselves collide, so re-validate once loaded.
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

    /** `com.example.<slug>`, matching Android Studio's suggestion for a fresh project. */
    private fun derivePackage(name: String): String {
        val slug = name.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "myapplication" }
        return "com.example.$slug"
    }

    /**
     * Recomputes both field errors. Duplicates are rejected here rather than left to
     * `uniqueProjectDir`'s numeric suffix, which silently produced "myapplication-2" for what the
     * user thought was a rename.
     */
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
            // Without this the button stays in its loading state forever on a failed generate.
            onError = { error ->
                updateState {
                    copy(creating = false, nameError = error.message ?: "Couldn't create the project")
                }
            },
        )
    }
}
