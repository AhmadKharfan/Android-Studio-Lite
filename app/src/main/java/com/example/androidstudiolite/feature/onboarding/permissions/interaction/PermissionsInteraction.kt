package com.example.androidstudiolite.feature.onboarding.permissions.interaction

sealed interface PermissionsInteraction {
    data class GrantPermission(val id: String) : PermissionsInteraction
}
