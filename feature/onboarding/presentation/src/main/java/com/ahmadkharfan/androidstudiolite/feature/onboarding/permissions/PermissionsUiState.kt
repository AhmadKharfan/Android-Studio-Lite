package com.ahmadkharfan.androidstudiolite.feature.onboarding.permissions
import androidx.compose.runtime.Immutable

sealed interface PermissionRequestKind {
    data class Runtime(val manifestPermissions: List<String>) : PermissionRequestKind
    data class SettingsScreen(val intentAction: String) : PermissionRequestKind
}

@Immutable
data class PermissionUiModel(
    val id: String,
    val icon: String,
    val title: String,
    val reason: String,
    val granted: Boolean,
    val optional: Boolean = false,
    val request: PermissionRequestKind? = null,
)

@Immutable
data class PermissionsUiState(
    val permissions: List<PermissionUiModel> = emptyList(),
    val canContinue: Boolean = false,
)

sealed interface PermissionsEffect {
    data class RequestRuntimePermissions(val permissions: List<String>) : PermissionsEffect
    data class OpenSettingsScreen(val intentAction: String) : PermissionsEffect
}
