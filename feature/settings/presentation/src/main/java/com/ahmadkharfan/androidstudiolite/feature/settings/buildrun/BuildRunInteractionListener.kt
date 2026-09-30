package com.ahmadkharfan.androidstudiolite.feature.settings.buildrun

import com.ahmadkharfan.androidstudiolite.core.gitauth.GitAuthPromptActions

interface BuildRunInteractionListener : GitAuthPromptActions {
    fun onToggleLaunchAfterInstall(enabled: Boolean)
    fun onToggleAabOutput(enabled: Boolean)
    fun onSelectBuildProvider(providerId: String)
    fun onConnectBuildAccess()
    fun onDisconnectBuildAccess()

    fun onOpenKeystoreDialog(mode: KeystoreDialogMode)
    fun onDismissKeystoreDialog()
    fun onCreateReleaseKeystore(form: KeystoreForm)
    fun onImportReleaseKeystore(form: KeystoreForm)
    fun onRemoveReleaseKeystore()

    fun onMessageShown()
}

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
