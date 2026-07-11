package com.example.androidstudiolite.feature.hub

import com.example.androidstudiolite.core.BaseViewModel
import com.example.androidstudiolite.domain.model.Project
import com.example.androidstudiolite.domain.repository.ProjectRepository
import com.example.androidstudiolite.feature.formatRelativeTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import java.util.Calendar

private const val CURRENT_VERSION = "1.1.3"
private const val LATEST_VERSION = "1.2.0"

class HubViewModel(
    private val projectRepository: ProjectRepository,
) : BaseViewModel<HubUiState, HubEffect>(
    initialState = HubUiState(greeting = greetingForNow()),
), HubInteractionListener {

    private var resumeDismissed = false
    private var dialogsInitialized = false
    private var resumeDialogShown = false
    private var updateDialogShown = false

    init {
        tryToCollect(
            block = { projectRepository.observeRecentProjects() },
            onCollect = { projects ->
                val sorted = projects.sortedByDescending { it.lastOpenedMillis ?: 0L }
                val models = sorted.map { it.toUiModel() }
                val resume = if (resumeDismissed) null else models.firstOrNull()
                updateState { copy(recentProjects = models, resumeProject = resume) }
                if (!dialogsInitialized) {
                    dialogsInitialized = true
                    showNextDialog(resume)
                }
            },
        )
        viewModelScope.launch {
            delay(350)
            updateState { copy(isLoadingRecents = false) }
        }
    }

    override fun onOpenProject(id: String) {
        emitEffect(HubEffect.NavigateToProject(id))
    }

    override fun onProjectMenu(id: String) {
        // No project actions menu yet.
    }

    override fun onResumeProject() {
        state.value.resumeProject?.let { emitEffect(HubEffect.NavigateToProject(it.id)) }
    }

    override fun onDismissResume() {
        resumeDismissed = true
        updateState { copy(resumeProject = null) }
    }

    override fun onCreateProject() {
        emitEffect(HubEffect.NavigateToCreateProject)
    }

    override fun onOpenProjectPicker() {
        updateState { copy(sheet = HubSheetUiState.OpenProject) }
    }

    override fun onCloneRepository() {
        updateState { copy(sheet = HubSheetUiState.CloneRepo) }
    }

    override fun onOpenPreferences() {
        emitEffect(HubEffect.NavigateToPreferences)
    }

    override fun onOpenTerminal() {
        emitEffect(HubEffect.NavigateToTerminal)
    }

    override fun onOpenIdeConfig() {
        emitEffect(HubEffect.NavigateToIdeConfig)
    }

    override fun onOpenDocs() {
        emitEffect(HubEffect.NavigateToDocs)
    }

    fun dismissSheet() {
        updateState { copy(sheet = HubSheetUiState.None) }
    }

    fun confirmResumeDialog() {
        val dialog = state.value.dialog
        if (dialog is HubDialogUiState.ResumeProject) {
            emitEffect(HubEffect.NavigateToProject(dialog.projectId))
        }
        showNextDialog(state.value.resumeProject)
    }

    fun dismissResumeDialog() {
        showNextDialog(state.value.resumeProject)
    }

    fun dismissUpdateDialog() {
        updateState { copy(dialog = HubDialogUiState.None) }
    }

    /** "Confirm" (Download) is a no-op in this fake environment — there's nothing to actually download. */
    fun confirmUpdateDialog() {
        updateState { copy(dialog = HubDialogUiState.None) }
    }

    /** Validates a folder picked via [com.example.androidstudiolite.feature.folderpicker] against known project paths. */
    fun handlePickedFolder(path: String) {
        val leafName = path.substringAfterLast('/')
        val match = state.value.recentProjects.firstOrNull { it.path.substringAfterLast('/') == leafName }
        if (match != null) {
            emitEffect(HubEffect.NavigateToProject(match.id))
        } else {
            updateState { copy(dialog = HubDialogUiState.InvalidFolder(path)) }
        }
    }

    fun dismissInvalidFolderDialog() {
        updateState { copy(dialog = HubDialogUiState.None) }
    }

    fun confirmInvalidFolderDialog(onReopenPicker: () -> Unit) {
        updateState { copy(dialog = HubDialogUiState.None) }
        onReopenPicker()
    }

    /** Shows the resume dialog once (if there's a resume candidate), then the update-available dialog once. */
    private fun showNextDialog(resume: HubProjectUiModel?) {
        val next = when {
            resume != null && !resumeDialogShown -> {
                resumeDialogShown = true
                HubDialogUiState.ResumeProject(resume.id, resume.name, resume.path)
            }
            !updateDialogShown -> {
                updateDialogShown = true
                HubDialogUiState.UpdateAvailable(
                    fromVersion = CURRENT_VERSION,
                    toVersion = LATEST_VERSION,
                    sizeMb = 48,
                    notes = "Faster Gradle sync, Kotlin 2.0 support and fixes for the file tree.",
                )
            }
            else -> HubDialogUiState.None
        }
        updateState { copy(dialog = next) }
    }

    private fun Project.toUiModel() = HubProjectUiModel(
        id = id,
        name = name,
        path = path,
        language = language,
        lastOpenedText = lastOpenedMillis?.let { formatRelativeTime(it) },
    )
}

private fun greetingForNow(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> "Good morning"
        hour < 18 -> "Good afternoon"
        else -> "Good evening"
    }
}
