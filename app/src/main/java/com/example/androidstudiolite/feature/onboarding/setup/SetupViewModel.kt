package com.example.androidstudiolite.feature.onboarding.setup

import com.example.androidstudiolite.core.BaseViewModel
import com.example.androidstudiolite.domain.repository.OnboardingRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewModelScope

class SetupViewModel(
    private val onboardingRepository: OnboardingRepository,
) : BaseViewModel<SetupUiState, Nothing>(
    initialState = SetupUiState(),
), SetupInteractionListener {

    private var started = false

    override fun onStartSetup() {
        if (started) return
        started = true
        viewModelScope.launch {
            updateState { copy(sdkStatus = InstallStatus.Installing, sdkProgressPercent = 0, sdkDetail = "0 / 274 MB") }
            val totalMb = 274
            var downloaded = 0
            while (downloaded < totalMb) {
                delay(220)
                downloaded = (downloaded + 42).coerceAtMost(totalMb)
                val percent = (downloaded * 100) / totalMb
                updateState { copy(sdkProgressPercent = percent, sdkDetail = "$downloaded / $totalMb MB") }
            }
            updateState { copy(sdkStatus = InstallStatus.Installed) }
            onboardingRepository.markSetupComplete()
            updateState { copy(setupComplete = true) }
        }
    }
}
