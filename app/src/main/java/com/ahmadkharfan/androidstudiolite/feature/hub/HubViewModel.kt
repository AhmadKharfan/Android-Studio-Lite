package com.ahmadkharfan.androidstudiolite.feature.hub

import android.content.Context
import com.ahmadkharfan.androidstudiolite.R
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.feature.formatRelativeTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import java.util.Calendar

class HubViewModel(
    private val projectRepository: ProjectRepository,
    private val appContext: Context,
) : BaseViewModel<HubUiState, HubEffect>(
    initialState = HubUiState(greeting = greetingForNow(appContext)),
), HubInteractionListener {

    private var resumeDismissed = false
    private var dialogsInitialized = false
    private var resumeDialogShown = false

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
        val project = state.value.recentProjects.firstOrNull { it.id == id } ?: return
        updateState { copy(projectMenu = project) }
    }

    fun dismissProjectMenu() {
        updateState { copy(projectMenu = null) }
    }

    fun requestRenameProject() {
        val project = state.value.projectMenu ?: return
        updateState { copy(projectMenu = null, dialog = HubDialogUiState.RenameProject(project.id, project.name)) }
    }

    fun requestDeleteProject() {
        val project = state.value.projectMenu ?: return
        updateState { copy(projectMenu = null, dialog = HubDialogUiState.DeleteProject(project.id, project.name)) }
    }

    fun confirmRenameProject(newName: String) {
        val dialog = state.value.dialog as? HubDialogUiState.RenameProject ?: return
        val trimmed = newName.trim()
        updateState { copy(dialog = HubDialogUiState.None) }
        if (trimmed.isEmpty() || trimmed == dialog.currentName) return
        viewModelScope.launch { runCatching { projectRepository.renameProject(dialog.id, trimmed) } }
    }

    fun confirmDeleteProject() {
        val dialog = state.value.dialog as? HubDialogUiState.DeleteProject ?: return
        updateState { copy(dialog = HubDialogUiState.None) }
        viewModelScope.launch { runCatching { projectRepository.deleteProject(dialog.id) } }
    }

    fun dismissProjectDialog() {
        updateState { copy(dialog = HubDialogUiState.None) }
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

    /** Validates a folder picked via [com.ahmadkharfan.androidstudiolite.feature.folderpicker] against known project paths. */
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

    /** Shows the resume dialog once, if there's a resume candidate. */
    private fun showNextDialog(resume: HubProjectUiModel?) {
        val next = if (resume != null && !resumeDialogShown) {
            resumeDialogShown = true
            HubDialogUiState.ResumeProject(resume.id, resume.name, resume.path)
        } else {
            HubDialogUiState.None
        }
        updateState { copy(dialog = next) }
    }

    private fun Project.toUiModel() = HubProjectUiModel(
        id = id,
        name = name,
        path = path,
        language = language,
        lastOpenedText = lastOpenedMillis?.let { formatRelativeTime(appContext, it) },
    )
}

private fun greetingForNow(context: Context): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when {
        hour < 12 -> context.getString(R.string.greeting_morning)
        hour < 18 -> context.getString(R.string.greeting_afternoon)
        else -> context.getString(R.string.greeting_evening)
    }
}
