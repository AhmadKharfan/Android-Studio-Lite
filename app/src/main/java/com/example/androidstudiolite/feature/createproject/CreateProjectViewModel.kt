package com.example.androidstudiolite.feature.createproject

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.model.ProjectNameValidation
import com.example.androidstudiolite.domain.model.validateProjectName
import com.example.androidstudiolite.domain.repository.ProjectRepository
import com.example.androidstudiolite.domain.repository.TemplateRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class CreateProjectViewModel(
    private val templateRepository: TemplateRepository,
    private val projectRepository: ProjectRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateProjectUiState())
    val uiState: StateFlow<CreateProjectUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val templates = templateRepository.getTemplates().map {
                CreateProjectTemplateUiModel(it.id, it.name, it.description, it.icon, it.tags)
            }
            _uiState.value = _uiState.value.copy(templates = templates, selectedTemplateId = templates.firstOrNull()?.id)
        }
    }

    fun onInteraction(interaction: CreateProjectInteraction) {
        when (interaction) {
            is CreateProjectInteraction.SelectTemplate -> _uiState.value = _uiState.value.copy(selectedTemplateId = interaction.id)
            CreateProjectInteraction.NextStep -> _uiState.value = _uiState.value.let { it.copy(step = (it.step + 1).coerceAtMost(2)) }
            CreateProjectInteraction.BackStep -> _uiState.value = _uiState.value.let { it.copy(step = (it.step - 1).coerceAtLeast(0)) }
            is CreateProjectInteraction.NameChanged -> applyNameChange(interaction.name)
            is CreateProjectInteraction.PackageChanged -> _uiState.value = _uiState.value.copy(packageName = interaction.packageName)
            is CreateProjectInteraction.LocationChanged -> _uiState.value = _uiState.value.copy(location = interaction.location)
            is CreateProjectInteraction.MinSdkChanged -> _uiState.value = _uiState.value.copy(minSdk = interaction.minSdk)
            CreateProjectInteraction.CreateProject -> startCreate()
        }
    }

    private fun applyNameChange(name: String) {
        val validation = validateProjectName(name)
        val slug = name.lowercase().filter { it.isLetterOrDigit() }.ifBlank { "myapplication" }
        _uiState.value = _uiState.value.copy(
            projectName = name,
            packageName = "com.example.$slug",
            location = "~/projects/$name",
            nameError = (validation as? ProjectNameValidation.Invalid)?.reason,
        )
    }

    private fun startCreate() {
        val state = _uiState.value
        if (state.creating) return
        _uiState.value = state.copy(creating = true)
        viewModelScope.launch {
            val templateId = state.selectedTemplateId ?: return@launch
            val project = projectRepository.createProject(state.projectName, state.packageName, templateId)
            _uiState.value = _uiState.value.copy(createdProjectId = project.id)
        }
    }
}
