package com.example.androidstudiolite.feature.onboarding.permissions.uiState

data class PermissionUiModel(
    val id: String,
    val icon: String,
    val title: String,
    val reason: String,
    val granted: Boolean,
)

data class PermissionsUiState(
    val permissions: List<PermissionUiModel> = emptyList(),
    val canContinue: Boolean = false,
)
