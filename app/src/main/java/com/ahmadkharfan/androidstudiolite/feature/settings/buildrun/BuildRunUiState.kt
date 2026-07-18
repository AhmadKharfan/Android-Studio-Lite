package com.ahmadkharfan.androidstudiolite.feature.settings.buildrun
import androidx.compose.runtime.Immutable

@Immutable
data class BuildRunUiState(
    val launchAfterInstall: Boolean = true,
    /** Release builds emit an .aab bundle instead of an .apk. */
    val buildOutputAab: Boolean = false,
    /**
     * When on, projects with a Git remote build by sending the remote URL + branch to the server
     * (no zip upload). Off = always zip-upload the local working tree (captures uncommitted changes).
     */
    val preferGitSource: Boolean = false,
    /** Path of the auto-managed debug keystore, shown read-only. */
    val debugKeystorePath: String = "",
    /** Summary of the configured release keystore ("path · alias"), or null when none is set. */
    val releaseKeystoreSummary: String? = null,
    /** Default path pre-filled into the "create keystore" dialog. */
    val suggestedReleaseKeystorePath: String = "",
    /** Non-null while the create/import keystore dialog is open. */
    val keystoreDialog: KeystoreDialogMode? = null,
    val keystoreBusy: Boolean = false,
    val keystoreError: String? = null,
    /** One-shot user message (snackbar), cleared via [BuildRunInteractionListener.onMessageShown]. */
    val message: String? = null,
) {
    val hasReleaseKeystore: Boolean get() = releaseKeystoreSummary != null
}

enum class KeystoreDialogMode { Create, Import }
