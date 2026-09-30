package com.ahmadkharfan.androidstudiolite.data.githubactions.readiness

import com.ahmadkharfan.androidstudiolite.data.githubactions.build.GitHubBuildMessages
import com.ahmadkharfan.androidstudiolite.data.githubactions.build.RepositorySetup
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildReadiness
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildState

/**
 * The build preflight's view of a [CloudBuildState]: whether it blocks a build, and the text to show.
 *
 * [CloudBuildState.Unknown] doesn't block: the check couldn't tell what GitHub meant, so the build is
 * allowed to try and report GitHub's own error, as before readiness was structured.
 */
internal fun CloudBuildState.toBuildReadiness(
    signInMessage: String,
    repositoryName: String,
    setup: RepositorySetup,
): BuildReadiness = when (this) {
    is CloudBuildState.Ready, is CloudBuildState.Unknown, CloudBuildState.Checking -> BuildReadiness.Ready
    CloudBuildState.NotConnected -> BuildReadiness.NeedsSignIn(signInMessage)
    CloudBuildState.Revoked -> BuildReadiness.NeedsSignIn("Your GitHub sign-in has expired or was revoked. $signInMessage")
    CloudBuildState.Offline -> BuildReadiness.Unavailable("You're offline. Cloud Build needs an internet connection.")
    CloudBuildState.GitHubUnavailable -> BuildReadiness.Unavailable("GitHub isn't responding. Try again in a moment.")
    is CloudBuildState.RateLimited -> BuildReadiness.Unavailable(GitHubBuildMessages.rateLimited(resetAtEpochSeconds))
    is CloudBuildState.PermissionUpdateRequired -> if (manageUrl == null && acceptedPermissions == null) {
        // The Git sign-in's OAuth token lacks `workflow`: only a new sign-in adds it.
        BuildReadiness.NeedsSignIn(GitHubBuildMessages.WORKFLOW_SCOPE_MISSING)
    } else {
        BuildReadiness.NeedsSetup(
            "Cloud Build needs updated access on GitHub to keep building. Review it here: ${manageUrl ?: INSTALLATIONS}",
        )
    }
    else -> BuildReadiness.NeedsSetup(setupReason(repositoryName, setup))
}

/** Why a build can't start until the App's access or the build repository is set up. */
private fun CloudBuildState.setupReason(repositoryName: String, setup: RepositorySetup): String = when (this) {
    is CloudBuildState.AccessMissing ->
        (setup as? RepositorySetup.Manual ?: RepositorySetup.Manual(installUrl)).message(repositoryName)
    is CloudBuildState.AccessPaused ->
        "Cloud Build's access to your GitHub account is paused. Resume it on GitHub: ${manageUrl ?: INSTALLATIONS}"
    is CloudBuildState.StorageMissing ->
        "Builds need a private repository named $repositoryName on your GitHub account. Create it, then build " +
            "again. Cloud Build's access is managed here: ${manageUrl ?: INSTALLATIONS}"
    is CloudBuildState.StorageNotReachable ->
        "Cloud Build can't reach your build storage ($repositoryName). It may have been deleted, renamed, or " +
            "removed from Cloud Build's access on GitHub. Check it here: ${manageUrl ?: INSTALLATIONS}"
    is CloudBuildState.StoragePublic ->
        "github.com/${storage.fullName} is public, and builds would publish your source code there. " +
            "Make it private, then build again."
    else -> "Cloud Build isn't set up yet."
}

private const val INSTALLATIONS = "https://github.com/settings/installations"
