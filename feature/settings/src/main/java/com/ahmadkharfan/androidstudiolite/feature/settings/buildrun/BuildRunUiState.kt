package com.ahmadkharfan.androidstudiolite.feature.settings.buildrun
import androidx.compose.runtime.Immutable

@Immutable
data class BuildRunUiState(
    val launchAfterInstall: Boolean = true,
    val buildOutputAab: Boolean = false,
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
