package com.example.androidstudiolite.data.fake

import com.example.androidstudiolite.domain.model.OnboardingPermission
import com.example.androidstudiolite.domain.model.OnboardingState
import com.example.androidstudiolite.domain.repository.OnboardingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class FakeOnboardingRepository : OnboardingRepository {

    private val state = MutableStateFlow(
        OnboardingState(
            permissions = listOf(
                OnboardingPermission(
                    id = "storage",
                    title = "Storage access",
                    reason = "Access your project files anywhere on device storage.",
                    granted = false,
                ),
                OnboardingPermission(
                    id = "install",
                    title = "Install apps",
                    reason = "Install the debug APKs you build.",
                    granted = false,
                ),
                OnboardingPermission(
                    id = "notifications",
                    title = "Notifications",
                    reason = "Alerts when long builds finish. Optional.",
                    granted = false,
                    optional = true,
                ),
            ),
        ),
    )

    override fun observeState(): StateFlow<OnboardingState> = state

    override suspend fun setPermissionGranted(id: String, granted: Boolean) {
        state.value = state.value.copy(
            permissions = state.value.permissions.map { if (it.id == id) it.copy(granted = granted) else it },
        )
    }

    override suspend fun markSetupComplete() {
        state.value = state.value.copy(setupComplete = true)
    }

    override suspend fun completeOnboarding() {
        state.value = state.value.copy(onboardingComplete = true)
    }
}
