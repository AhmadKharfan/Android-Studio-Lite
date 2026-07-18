package com.ahmadkharfan.androidstudiolite.feature.settings.buildrun

interface BuildRunInteractionListener {
    fun onToggleLaunchAfterInstall(enabled: Boolean)
    fun onToggleAabOutput(enabled: Boolean)
    fun onTogglePreferGitSource(enabled: Boolean)

    // Release keystore management.
    fun onOpenKeystoreDialog(mode: KeystoreDialogMode)
    fun onDismissKeystoreDialog()
    fun onCreateReleaseKeystore(form: KeystoreForm)
    fun onImportReleaseKeystore(form: KeystoreForm)
    fun onRemoveReleaseKeystore()

    fun onMessageShown()
}

/** Fields the create/import keystore dialog collects; only the relevant subset is used per mode. */
data class KeystoreForm(
    val storePath: String,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
    val validityYears: String = "25",
    val commonName: String = "",
    val organization: String = "",
    val country: String = "",
)
