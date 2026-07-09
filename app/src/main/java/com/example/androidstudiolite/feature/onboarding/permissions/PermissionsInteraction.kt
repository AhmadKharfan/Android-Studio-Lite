package com.example.androidstudiolite.feature.onboarding.permissions
sealed interface PermissionsInteraction {
    data class GrantPermission(val id: String) : PermissionsInteraction
}
