package com.ahmadkharfan.androidstudiolite.feature.settings.ideconfig

import androidx.lifecycle.viewModelScope
import com.ahmadkharfan.androidstudiolite.core.BaseViewModel
import com.ahmadkharfan.androidstudiolite.core.network.NetworkMonitor
import com.ahmadkharfan.androidstudiolite.domain.model.IdeEnvironmentComponentState
import com.ahmadkharfan.androidstudiolite.domain.model.IdeEnvironmentComponentStatus
import com.ahmadkharfan.androidstudiolite.domain.repository.IdeEnvironmentRepository
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val COMPONENT_ICONS = mapOf(
    "jdk" to "coffee",
    "android-sdk-platform" to "smartphone",
    "android-build-tools" to "hammer",
    "gradle" to "cog",
)
private const val DEFAULT_COMPONENT_ICON = "package"

class IdeConfigViewModel(
    private val ideEnvironmentRepository: IdeEnvironmentRepository,
    private val networkMonitor: NetworkMonitor,
) : BaseViewModel<IdeConfigUiState, Nothing>(
    initialState = IdeConfigUiState(),
), IdeConfigInteractionListener {

    init {
        viewModelScope.launch { ideEnvironmentRepository.refresh() }

        tryToCollect(
            block = { ideEnvironmentRepository.observeState() },
            onCollect = { env ->
                updateState {
                    copy(
                        components = env.components.map { it.toUiModel() },
                        isInstalling = env.isInstalling,
                        unsupportedDevice = env.abi == null,
                    )
                }
            },
        )

        tryToCollect(
            block = { networkMonitor.observeOnline() },
            onCollect = { online -> updateState { copy(networkAvailable = online) } },
        )
    }

    override fun onInstallComponent(id: String) {
        if (state.value.isInstalling) return
        viewModelScope.launch { ideEnvironmentRepository.installAll() }
    }

    override fun onRetryConnection() {
        viewModelScope.launch { ideEnvironmentRepository.refresh() }
    }

    private fun IdeEnvironmentComponentState.toUiModel() = IdeComponentUiModel(
        id = id,
        icon = COMPONENT_ICONS[id] ?: DEFAULT_COMPONENT_ICON,
        title = displayName,
        subtitle = "$version · ${sizeLabel()}",
        status = status.toUiStatus(),
        progressPercent = progressPercent,
        errorMessage = errorMessage,
    )

    private fun IdeEnvironmentComponentState.sizeLabel(): String {
        if (sizeBytes <= 0) return version
        val downloadedMb = downloadedBytes / (1024.0 * 1024.0)
        val totalMb = sizeBytes / (1024.0 * 1024.0)
        return "${downloadedMb.roundToInt()} / ${totalMb.roundToInt()} MB"
    }

    private fun IdeEnvironmentComponentStatus.toUiStatus(): IdeConfigComponentStatus = when (this) {
        IdeEnvironmentComponentStatus.NotInstalled -> IdeConfigComponentStatus.NotInstalled
        IdeEnvironmentComponentStatus.Downloading -> IdeConfigComponentStatus.Downloading
        IdeEnvironmentComponentStatus.Verifying -> IdeConfigComponentStatus.Verifying
        IdeEnvironmentComponentStatus.Extracting -> IdeConfigComponentStatus.Extracting
        IdeEnvironmentComponentStatus.Installed -> IdeConfigComponentStatus.Installed
        IdeEnvironmentComponentStatus.Failed -> IdeConfigComponentStatus.Failed
    }
}
