package com.example.androidstudiolite.feature.onboarding.permissions

import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.core.BaseViewModel
import com.example.androidstudiolite.core.permissions.AslPermissionId
import com.example.androidstudiolite.core.permissions.AslPermissionRequest
import com.example.androidstudiolite.core.permissions.AslPermissions
import com.example.androidstudiolite.domain.repository.OnboardingRepository
import kotlinx.coroutines.launch

private val PERMISSION_ICONS = mapOf(
    "storage" to "folder-lock",
    "install" to "package",
    "notifications" to "bell",
)

class PermissionsViewModel(
    private val onboardingRepository: OnboardingRepository,
) : BaseViewModel<PermissionsUiState, PermissionsEffect>(
    initialState = PermissionsUiState(),
), PermissionsInteractionListener {

    init {
        // The repository starts with a stale (never-checked) permission list; force one live read
        // immediately so the screen doesn't briefly show everything as "not granted".
        viewModelScope.launch { onboardingRepository.refreshPermissions() }

        tryToCollect(
            block = { onboardingRepository.observeState() },
            onCollect = { state ->
                val models = state.permissions.map { permission ->
                    val descriptor = AslPermissionId.fromDomainId(permission.id)
                        ?.let { id -> AslPermissions.descriptors().firstOrNull { it.id == id } }
                    PermissionUiModel(
                        id = permission.id,
                        icon = PERMISSION_ICONS[permission.id] ?: "folder-lock",
                        title = permission.title,
                        reason = permission.reason,
                        granted = permission.granted,
                        optional = permission.optional,
                        request = descriptor?.request?.toUiKind(),
                    )
                }
                updateState {
                    copy(
                        permissions = models,
                        canContinue = models.filterNot { it.optional }.all { it.granted },
                    )
                }
            },
        )
    }

    override fun onGrantPermission(id: String) {
        val descriptor = state.value.permissions.firstOrNull { it.id == id } ?: return
        when (val request = descriptor.request) {
            is PermissionRequestKind.Runtime -> emitEffect(PermissionsEffect.RequestRuntimePermissions(request.manifestPermissions))
            is PermissionRequestKind.SettingsScreen -> emitEffect(PermissionsEffect.OpenSettingsScreen(request.intentAction))
            null -> Unit
        }
    }

    /** Called by the screen after a permission dialog or Settings screen returns (or on resume). */
    fun onPermissionsUpdated() {
        viewModelScope.launch { onboardingRepository.refreshPermissions() }
    }

    private fun AslPermissionRequest.toUiKind(): PermissionRequestKind = when (this) {
        is AslPermissionRequest.Runtime -> PermissionRequestKind.Runtime(manifestPermissions)
        is AslPermissionRequest.SettingsScreen -> PermissionRequestKind.SettingsScreen(intentAction)
    }
}
