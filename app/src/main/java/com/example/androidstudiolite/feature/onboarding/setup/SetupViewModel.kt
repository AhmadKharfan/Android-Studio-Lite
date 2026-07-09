package com.example.androidstudiolite.feature.onboarding.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.androidstudiolite.domain.repository.OnboardingRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SetupViewModel(
    private val onboardingRepository: OnboardingRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private var started = false

    fun onInteraction(interaction: SetupInteraction) {
        when (interaction) {
            SetupInteraction.StartSetup -> startSetup()
        }
    }

    private fun startSetup() {
        if (started) return
        started = true
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(sdkStatus = InstallStatus.Installing, sdkProgressPercent = 0, sdkDetail = "0 / 274 MB")
            val totalMb = 274
            var downloaded = 0
            while (downloaded < totalMb) {
                delay(220)
                downloaded = (downloaded + 42).coerceAtMost(totalMb)
                val percent = (downloaded * 100) / totalMb
                _uiState.value = _uiState.value.copy(sdkProgressPercent = percent, sdkDetail = "$downloaded / $totalMb MB")
            }
            _uiState.value = _uiState.value.copy(sdkStatus = InstallStatus.Installed)
            onboardingRepository.markSetupComplete()
            _uiState.value = _uiState.value.copy(setupComplete = true)
        }
    }
}
