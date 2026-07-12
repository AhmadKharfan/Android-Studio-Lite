package com.ahmadkharfan.androidstudiolite.feature.onboarding.setup

import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.domain.model.IdeEnvironmentComponentState
import com.ahmadkharfan.androidstudiolite.domain.model.IdeEnvironmentComponentStatus
import com.ahmadkharfan.androidstudiolite.domain.repository.IdeEnvironmentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.OnboardingRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val COMPONENT_ICONS = mapOf(
    "jdk" to "coffee",
    "android-sdk-platform" to "smartphone",
    "android-build-tools" to "hammer",
    "gradle" to "cog",
)
private const val DEFAULT_COMPONENT_ICON = "package"

class SetupViewModel(
    private val ideEnvironmentRepository: IdeEnvironmentRepository,
    private val onboardingRepository: OnboardingRepository,
) : BaseViewModel<SetupUiState, Nothing>(
    initialState = SetupUiState(),
), SetupInteractionListener {

    init {
        viewModelScope.launch { ideEnvironmentRepository.refresh() }

        tryToCollect(
            block = { ideEnvironmentRepository.observeState() },
            onCollect = { env ->
                val models = env.components.map { component ->
                    SetupComponentUiModel(
                        id = component.id,
                        icon = COMPONENT_ICONS[component.id] ?: DEFAULT_COMPONENT_ICON,
                        displayName = component.displayName,
                        version = component.version,
                        status = component.status.toUiStatus(),
                        progressPercent = component.progressPercent,
                        detail = component.sizeLabel(),
                        errorMessage = component.errorMessage,
                    )
                }
                updateState {
                    copy(
                        components = models,
                        isInstalling = env.isInstalling,
                        allInstalled = env.allInstalled,
                        unsupportedDevice = env.abi == null,
                    )
                }
                if (env.allInstalled && !state.value.setupComplete) {
                    onboardingRepository.markSetupComplete()
                    updateState { copy(setupComplete = true) }
                }
            },
        )
    }

    override fun onStartSetup() {
        if (state.value.isInstalling) return
        viewModelScope.launch { ideEnvironmentRepository.installAll() }
    }
}

private fun IdeEnvironmentComponentState.sizeLabel(): String {
    if (sizeBytes <= 0) return ""
    val downloading = downloadedBytes in 1 until sizeBytes
    return if (downloading) {
        "${(downloadedBytes / (1024.0 * 1024.0)).roundToInt()} / ${(sizeBytes / (1024.0 * 1024.0)).roundToInt()} MB"
    } else {
        humanSize(sizeBytes)
    }
}

private fun humanSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "${(bytes / (1024.0 * 1024.0)).roundToInt()} MB"
    bytes >= 1024 -> "${(bytes / 1024.0).roundToInt()} KB"
    else -> "$bytes B"
}

private fun IdeEnvironmentComponentStatus.toUiStatus(): SetupComponentStatus = when (this) {
    IdeEnvironmentComponentStatus.NotInstalled -> SetupComponentStatus.NotInstalled
    IdeEnvironmentComponentStatus.Downloading -> SetupComponentStatus.Downloading
    IdeEnvironmentComponentStatus.Verifying -> SetupComponentStatus.Verifying
    IdeEnvironmentComponentStatus.Extracting -> SetupComponentStatus.Extracting
    IdeEnvironmentComponentStatus.Installed -> SetupComponentStatus.Installed
    IdeEnvironmentComponentStatus.Failed -> SetupComponentStatus.Failed
}
