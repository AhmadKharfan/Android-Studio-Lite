package com.example.androidstudiolite.feature.onboarding.permissions

import com.example.androidstudiolite.core.BaseViewModel
import com.example.androidstudiolite.domain.repository.OnboardingRepository
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

private val PERMISSION_ICONS = mapOf(
    "storage" to "folder-lock",
    "install" to "package",
    "notifications" to "bell",
)

private val REQUIRED_PERMISSION_IDS = setOf("storage", "install")

class PermissionsViewModel(
    private val onboardingRepository: OnboardingRepository,
) : BaseViewModel<PermissionsUiState, Nothing>(
    initialState = PermissionsUiState(),
), PermissionsInteractionListener {

    init {
        tryToCollect(
            block = { onboardingRepository.observeState() },
            onCollect = { state ->
                val models = state.permissions.map {
                    PermissionUiModel(
                        id = it.id,
                        icon = PERMISSION_ICONS[it.id] ?: "folder-lock",
                        title = it.title,
                        reason = it.reason,
                        granted = it.granted,
                    )
                }
                updateState {
                    copy(
                        permissions = models,
                        canContinue = models.filter { it.id in REQUIRED_PERMISSION_IDS }.all { it.granted },
                    )
                }
            },
        )
    }

    override fun onGrantPermission(id: String) {
        viewModelScope.launch {
            onboardingRepository.setPermissionGranted(id, true)
        }
    }
}
