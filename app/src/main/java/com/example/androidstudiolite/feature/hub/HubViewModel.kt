package com.example.androidstudiolite.feature.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.model.Project
import com.example.androidstudiolite.domain.repository.ProjectRepository
import com.example.androidstudiolite.feature.formatRelativeTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Calendar

private const val CURRENT_VERSION = "1.1.3"
private const val LATEST_VERSION = "1.2.0"

class HubViewModel(
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HubUiState(greeting = greetingForNow()))
    val uiState: StateFlow<HubUiState> = _uiState.asStateFlow()

    private var resumeDismissed = false
    private var dialogsInitialized = false
    private var resumeDialogShown = false
    private var updateDialogShown = false

    init {
        viewModelScope.launch {
            projectRepository.observeRecentProjects().collect { projects ->
                val sorted = projects.sortedByDescending { it.lastOpenedMillis ?: 0L }
                val models = sorted.map { it.toUiModel() }
                val resume = if (resumeDismissed) null else models.firstOrNull()
                _uiState.value = _uiState.value.copy(recentProjects = models, resumeProject = resume)
                if (!dialogsInitialized) {
                    dialogsInitialized = true
                    showNextDialog(resume)
                }
            }
        }
        viewModelScope.launch {
            delay(350)
            _uiState.value = _uiState.value.copy(isLoadingRecents = false)
        }
    }

    fun dismissResume() {
        resumeDismissed = true
        _uiState.value = _uiState.value.copy(resumeProject = null)
    }

    fun confirmResumeDialog(onNavigate: (String) -> Unit) {
        val dialog = _uiState.value.dialog
        if (dialog is HubDialogUiState.ResumeProject) {
            onNavigate(dialog.projectId)
        }
        showNextDialog(_uiState.value.resumeProject)
    }

    fun dismissResumeDialog() {
        showNextDialog(_uiState.value.resumeProject)
    }

    fun dismissUpdateDialog() {
        _uiState.value = _uiState.value.copy(dialog = HubDialogUiState.None)
    }

    /** "Confirm" (Download) is a no-op in this fake environment — there's nothing to actually download. */
    fun confirmUpdateDialog() {
        _uiState.value = _uiState.value.copy(dialog = HubDialogUiState.None)
    }

    /** Validates a folder picked via [com.example.androidstudiolite.feature.folderpicker] against known project paths. */
    fun handlePickedFolder(path: String, onValidProjectOpened: (String) -> Unit) {
        val leafName = path.substringAfterLast('/')
        val match = _uiState.value.recentProjects.firstOrNull { it.path.substringAfterLast('/') == leafName }
        if (match != null) {
            onValidProjectOpened(match.id)
        } else {
            _uiState.value = _uiState.value.copy(dialog = HubDialogUiState.InvalidFolder(path))
        }
    }

    fun dismissInvalidFolderDialog() {
        _uiState.value = _uiState.value.copy(dialog = HubDialogUiState.None)
    }

    fun confirmInvalidFolderDialog(onReopenPicker: () -> Unit) {
        _uiState.value = _uiState.value.copy(dialog = HubDialogUiState.None)
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
        _uiState.value = _uiState.value.copy(dialog = next)
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
