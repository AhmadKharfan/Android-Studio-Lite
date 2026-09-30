package com.ahmadkharfan.androidstudiolite.feature.settings.buildrun
import androidx.compose.runtime.Immutable
import com.ahmadkharfan.androidstudiolite.core.gitauth.cloudbuild.CloudBuildSetupUiState

@Immutable
data class BuildRunUiState(
    val launchAfterInstall: Boolean = true,
    val buildOutputAab: Boolean = false,
    val buildProviders: List<String> = emptyList(),
    val selectedBuildProvider: String = "",
    val buildAccess: BuildAccessUiState? = null,
    val cloudBuild: CloudBuildSetupUiState = CloudBuildSetupUiState(),
    val debugKeystorePath: String = "",
    val releaseKeystoreSummary: String? = null,
    val suggestedReleaseKeystorePath: String = "",
    val keystoreDialog: KeystoreDialogMode? = null,
    val keystoreBusy: Boolean = false,
    val keystoreError: String? = null,
    val message: String? = null,
) {
    val hasReleaseKeystore: Boolean get() = releaseKeystoreSummary != null
}

enum class KeystoreDialogMode { Create, Import }

/** GitHub Actions builds' own GitHub App sign-in; absent when builds reuse the Git sign-in. */
@Immutable
data class BuildAccessUiState(
    val connected: Boolean,
    val installUrl: String,
)
