package com.example.androidstudiolite.feature.onboarding.permissions.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.usecase.ObserveOnboardingStateUseCase
import com.example.androidstudiolite.domain.usecase.UpdatePermissionUseCase
import com.example.androidstudiolite.feature.onboarding.permissions.interaction.PermissionsInteraction
import com.example.androidstudiolite.feature.onboarding.permissions.uiState.PermissionUiModel
import com.example.androidstudiolite.feature.onboarding.permissions.uiState.PermissionsUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private val PERMISSION_ICONS = mapOf(
    "storage" to "folder-lock",
    "install" to "package",
    "notifications" to "bell",
)

private val REQUIRED_PERMISSION_IDS = setOf("storage", "install")

class PermissionsViewModel(
    private val observeOnboardingState: ObserveOnboardingStateUseCase,
    private val updatePermission: UpdatePermissionUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PermissionsUiState())
    val uiState: StateFlow<PermissionsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeOnboardingState().collect { state ->
                val models = state.permissions.map {
                    PermissionUiModel(
                        id = it.id,
                        icon = PERMISSION_ICONS[it.id] ?: "folder-lock",
                        title = it.title,
                        reason = it.reason,
                        granted = it.granted,
                    )
                }
                _uiState.value = PermissionsUiState(
                    permissions = models,
                    canContinue = models.filter { it.id in REQUIRED_PERMISSION_IDS }.all { it.granted },
                )
            }
        }
    }

    fun onInteraction(interaction: PermissionsInteraction) {
        when (interaction) {
            is PermissionsInteraction.GrantPermission -> viewModelScope.launch {
                updatePermission(interaction.id, true)
            }
        }
    }
}
