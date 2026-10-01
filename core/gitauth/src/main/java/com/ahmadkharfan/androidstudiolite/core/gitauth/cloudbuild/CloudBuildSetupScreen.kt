package com.ahmadkharfan.androidstudiolite.core.gitauth.cloudbuild

import androidx.compose.runtime.Immutable
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildState

/** What the Cloud Build setup shows for one readiness state: which step, and what its buttons do. */
@Immutable
data class CloudBuildSetupScreen(
    val step: CloudBuildStep,
    val primary: CloudBuildAction?,
    val secondary: CloudBuildAction? = null,
    /** The GitHub account builds use, when known. */
    val account: String? = null,
    /** When GitHub's API limit resets, in epoch seconds, for [CloudBuildStep.RateLimited]. */
    val resetAtEpochSeconds: Long? = null,
    /** GitHub's own words, for [CloudBuildStep.Unknown]. */
    val detail: String? = null,
)

enum class CloudBuildStep {
    Checking,
    Connect,
    Reconnect,
    AllowAccess,
    AccessPaused,
    CreateStorage,
    StorageNotReachable,
    StoragePublic,
    ApproveUpdate,
    Offline,
    GitHubUnavailable,
    RateLimited,
    Unknown,
    Ready,
}

/** A button in the setup. */
sealed interface CloudBuildAction {
    /** Sign in to GitHub for builds, in the app. */
    data object Connect : CloudBuildAction

    /** Ask GitHub again. */
    data object CheckAgain : CloudBuildAction

    /** Open [url] on GitHub; the setup re-checks when the user comes back. */
    data class OpenGitHub(val url: String, val purpose: Purpose) : CloudBuildAction

    /** Create the private build storage from the app, with a GitHub sign-in that's allowed to. */
    data object CreateStorage : CloudBuildAction

    enum class Purpose { AllowAccess, ManageAccess, CreateStorage, StorageSettings, ReviewAccess }
}

/** GitHub's new-repository form, filled in for the build storage (documented `github.com/new` parameters). */
const val CREATE_STORAGE_URL =
    "https://github.com/new?name=asl-build&visibility=private&description=Private%20build%20storage%20for%20Android%20Studio%20Lite"

/** GitHub's list of installed apps, for when GitHub didn't give an installation's own link. */
const val INSTALLATIONS_URL = "https://github.com/settings/installations"

/**
 * The setup screen for [state]. Every link comes from the readiness state itself (GitHub's own
 * `html_url`s) or from a documented GitHub URL; none is built from ids.
 *
 * @param installUrl where to install the build App when it isn't installed and the state carries no link.
 * @param storage whether the app can create the build storage itself, and whether it just did.
 */
fun cloudBuildSetupScreen(
    state: CloudBuildState?,
    installUrl: String?,
    storage: StorageCreationOptions = StorageCreationOptions(),
): CloudBuildSetupScreen = when (state) {
    null, CloudBuildState.Checking -> CloudBuildSetupScreen(CloudBuildStep.Checking, primary = null)
    CloudBuildState.NotConnected -> CloudBuildSetupScreen(CloudBuildStep.Connect, CloudBuildAction.Connect)
    CloudBuildState.Revoked -> CloudBuildSetupScreen(CloudBuildStep.Reconnect, CloudBuildAction.Connect)
    is CloudBuildState.Ready -> CloudBuildSetupScreen(CloudBuildStep.Ready, primary = null, account = state.account)
    CloudBuildState.Offline -> CloudBuildSetupScreen(CloudBuildStep.Offline, CloudBuildAction.CheckAgain)
    CloudBuildState.GitHubUnavailable -> CloudBuildSetupScreen(CloudBuildStep.GitHubUnavailable, CloudBuildAction.CheckAgain)
    is CloudBuildState.RateLimited ->
        CloudBuildSetupScreen(CloudBuildStep.RateLimited, CloudBuildAction.CheckAgain, resetAtEpochSeconds = state.resetAtEpochSeconds)
    is CloudBuildState.Unknown ->
        CloudBuildSetupScreen(CloudBuildStep.Unknown, CloudBuildAction.CheckAgain, detail = state.message)
    else -> accessScreen(state, installUrl, storage)
}

/**
 * Whether the setup offers to create the build storage from the app ([canCreate]), and whether it just
 * created it or found it ([created]), after which only allowing access is left.
 */
@Immutable
data class StorageCreationOptions(val canCreate: Boolean = false, val created: Boolean = false)

/** Screens that send the user to GitHub to fix access or the build storage. */
private fun accessScreen(state: CloudBuildState, installUrl: String?, storage: StorageCreationOptions): CloudBuildSetupScreen {
    val createStorage = createStorageAction(storage)
    return when (state) {
        is CloudBuildState.AccessMissing -> CloudBuildSetupScreen(
            CloudBuildStep.AllowAccess,
            CloudBuildAction.OpenGitHub(state.installUrl ?: installUrl ?: INSTALLATIONS_URL, CloudBuildAction.Purpose.AllowAccess),
            secondary = createStorage,
        )
        is CloudBuildState.AccessPaused -> CloudBuildSetupScreen(CloudBuildStep.AccessPaused, manage(state.manageUrl))
        is CloudBuildState.StorageMissing -> missingStorageScreen(createStorage)
        is CloudBuildState.StorageNotReachable ->
            CloudBuildSetupScreen(CloudBuildStep.StorageNotReachable, manage(state.manageUrl), secondary = createStorage)
        is CloudBuildState.StoragePublic -> CloudBuildSetupScreen(
            CloudBuildStep.StoragePublic,
            CloudBuildAction.OpenGitHub(
                "${state.storage.htmlUrl ?: "https://github.com/${state.storage.fullName}"}/settings",
                CloudBuildAction.Purpose.StorageSettings,
            ),
        )
        is CloudBuildState.PermissionUpdateRequired -> permissionScreen(state)
        else -> CloudBuildSetupScreen(CloudBuildStep.Unknown, CloudBuildAction.CheckAgain)
    }
}

private val manualCreateStorage = CloudBuildAction.OpenGitHub(CREATE_STORAGE_URL, CloudBuildAction.Purpose.CreateStorage)

private fun manage(url: String?) = CloudBuildAction.OpenGitHub(url ?: INSTALLATIONS_URL, CloudBuildAction.Purpose.ManageAccess)

/** How the build storage can be created now: by the app, on GitHub, or not at all once it's done. */
private fun createStorageAction(storage: StorageCreationOptions): CloudBuildAction? = when {
    storage.created -> null
    storage.canCreate -> CloudBuildAction.CreateStorage
    else -> manualCreateStorage
}

private fun missingStorageScreen(createStorage: CloudBuildAction?) = CloudBuildSetupScreen(
    CloudBuildStep.CreateStorage,
    createStorage ?: CloudBuildAction.CheckAgain,
    // When the app creates it, GitHub's form stays available as the manual way.
    secondary = manualCreateStorage.takeIf { createStorage == CloudBuildAction.CreateStorage },
)

private fun permissionScreen(state: CloudBuildState.PermissionUpdateRequired): CloudBuildSetupScreen =
    if (state.manageUrl == null && state.acceptedPermissions == null) {
        // The Git sign-in's OAuth token lacks a scope: signing in again grants it.
        CloudBuildSetupScreen(CloudBuildStep.Reconnect, CloudBuildAction.Connect)
    } else {
        CloudBuildSetupScreen(
            CloudBuildStep.ApproveUpdate,
            CloudBuildAction.OpenGitHub(state.manageUrl ?: INSTALLATIONS_URL, CloudBuildAction.Purpose.ReviewAccess),
        )
    }
