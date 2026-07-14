package com.ahmadkharfan.androidstudiolite.feature.settings.buildrun

interface BuildRunInteractionListener {
    fun onGradleJvmPathChanged(path: String)
    fun onToggleParallelTaskExecution(enabled: Boolean)
    fun onToggleBuildCache(enabled: Boolean)
    fun onToggleConfigurationCache(enabled: Boolean)
    fun onToggleLaunchAfterInstall(enabled: Boolean)
    fun onToggleInstallViaShizuku(enabled: Boolean)
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
