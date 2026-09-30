package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import kotlinx.coroutines.flow.StateFlow

/**
 * Whether Cloud Build can build right now, and if not, what the user has to fix.
 *
 * [check] asks GitHub again and never trusts an earlier answer, so it is what to call on Run, when
 * settings open, after the user returns from GitHub, and on "Check again". Concurrent calls share one
 * check. [state] holds the latest answer, [CloudBuildState.Checking] while one is running, and null
 * until the first check.
 */
interface CloudBuildReadiness {
    val state: StateFlow<CloudBuildState?>

    suspend fun check(): CloudBuildState
}

/**
 * One Cloud Build readiness answer. Each state is something GitHub actually reported: where GitHub
 * can't tell two situations apart, the state says so instead of guessing (see [StorageNotReachable]).
 */
sealed interface CloudBuildState {

    /** A check is running. */
    data object Checking : CloudBuildState

    /** No GitHub connection for builds on this device. */
    data object NotConnected : CloudBuildState

    /** GitHub no longer accepts this device's connection (revoked or expired); connecting again fixes it. */
    data object Revoked : CloudBuildState

    /** The build GitHub App isn't installed on the user's account. [installUrl] installs it, when known. */
    data class AccessMissing(val installUrl: String?) : CloudBuildState

    /** The App is installed but suspended on the user's account. */
    data class AccessPaused(val manageUrl: String?) : CloudBuildState

    /**
     * The App can see all of the user's repositories and none of them is the build storage, so it
     * doesn't exist (under its name, or under the id remembered for it).
     */
    data class StorageMissing(val manageUrl: String?) : CloudBuildState

    /**
     * The App is limited to selected repositories and the build storage isn't one of them. GitHub doesn't
     * say whether it was deleted, renamed out of reach, or never added: all look the same to the App.
     * [wasReachableBefore] is true when this device had reached it earlier.
     */
    data class StorageNotReachable(val manageUrl: String?, val wasReachableBefore: Boolean) : CloudBuildState

    /** The build storage is public; building would publish the project's source. */
    data class StoragePublic(val storage: CloudBuildStorage) : CloudBuildState

    /**
     * GitHub refused a call for lack of permission. [acceptedPermissions] is what GitHub said the call
     * needs, when it said.
     */
    data class PermissionUpdateRequired(val manageUrl: String?, val acceptedPermissions: String?) : CloudBuildState

    /** Ready to build as [account]. [storage] is null when the provider creates its storage itself. */
    data class Ready(val account: String, val storage: CloudBuildStorage?) : CloudBuildState

    /** The device has no internet connection. */
    data object Offline : CloudBuildState

    /** GitHub couldn't be reached or answered with a server error. */
    data object GitHubUnavailable : CloudBuildState

    /** GitHub's API limit is used up; [resetAtEpochSeconds] is when it resets, when GitHub said. */
    data class RateLimited(val resetAtEpochSeconds: Long?) : CloudBuildState

    /** GitHub answered in a way the check doesn't recognise; [message] is GitHub's own text. */
    data class Unknown(val message: String) : CloudBuildState
}

/** The private repository builds run from, identified by [id] so a rename doesn't lose it. */
data class CloudBuildStorage(val id: Long?, val fullName: String, val htmlUrl: String?, val isPrivate: Boolean)
