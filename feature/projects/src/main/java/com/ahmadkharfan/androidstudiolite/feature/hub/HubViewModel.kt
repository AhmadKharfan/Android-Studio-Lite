package com.ahmadkharfan.androidstudiolite.feature.hub

import android.content.Context
import com.ahmadkharfan.androidstudiolite.feature.projects.R
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.feature.formatRelativeTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope
import java.util.Calendar

class HubViewModel(
    private val projectRepository: ProjectRepository,
    private val preferencesRepository: PreferencesRepository,
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

    override fun onDismissProjectMenu() {
        updateState { copy(projectMenu = null) }
    }

    override fun onRequestRenameProject() {
        val project = state.value.projectMenu ?: return
        updateState { copy(projectMenu = null, dialog = HubDialogUiState.RenameProject(project.id, project.name)) }
    }

    override fun onRequestDeleteProject() {
        val project = state.value.projectMenu ?: return
        updateState { copy(projectMenu = null, dialog = HubDialogUiState.DeleteProject(project.id, project.name)) }
    }

    override fun onConfirmRenameProject(newName: String) {
        val dialog = state.value.dialog as? HubDialogUiState.RenameProject ?: return
        val trimmed = newName.trim()
        updateState { copy(dialog = HubDialogUiState.None) }
        if (trimmed.isEmpty() || trimmed == dialog.currentName) return
        viewModelScope.launch { runCatching { projectRepository.renameProject(dialog.id, trimmed) } }
    }

    override fun onConfirmDeleteProject() {
        val dialog = state.value.dialog as? HubDialogUiState.DeleteProject ?: return
        updateState { copy(dialog = HubDialogUiState.None) }
        viewModelScope.launch { runCatching { projectRepository.deleteProject(dialog.id) } }
    }

    override fun onDismissProjectDialog() {
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

    override fun onDismissSheet() {
        updateState { copy(sheet = HubSheetUiState.None) }
    }

    override fun onConfirmResumeDialog() {
        val dialog = state.value.dialog
        if (dialog is HubDialogUiState.ResumeProject) {
            emitEffect(HubEffect.NavigateToProject(dialog.projectId))
        }
        showNextDialog(state.value.resumeProject)
    }

    override fun onDismissResumeDialog() {
        showNextDialog(state.value.resumeProject)
    }

    override fun onFolderPicked(path: String) {
        val leafName = path.substringAfterLast('/')
        val match = state.value.recentProjects.firstOrNull { it.path.substringAfterLast('/') == leafName }
        if (match != null) {
            emitEffect(HubEffect.NavigateToProject(match.id))
        } else {
            updateState { copy(dialog = HubDialogUiState.InvalidFolder(path)) }
        }
    }

    override fun onDismissInvalidFolderDialog() {
        updateState { copy(dialog = HubDialogUiState.None) }
    }

    override fun onConfirmInvalidFolderDialog(onReopenPicker: () -> Unit) {
        updateState { copy(dialog = HubDialogUiState.None) }
        onReopenPicker()
    }

    private fun showNextDialog(resume: HubProjectUiModel?) {
        viewModelScope.launch {
            val autoOpen = preferencesRepository.observePreferences().first().autoOpenLastProject
            val next = if (resume != null && !resumeDialogShown && !autoOpen) {
                resumeDialogShown = true
                HubDialogUiState.ResumeProject(resume.id, resume.name, resume.path)
            } else {
                if (resume != null && autoOpen) resumeDialogShown = true
                HubDialogUiState.None
            }
            updateState { copy(dialog = next) }
        }
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
