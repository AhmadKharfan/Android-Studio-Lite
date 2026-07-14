package com.ahmadkharfan.androidstudiolite.data.build.install

import android.content.pm.PackageInstaller

/** Progress of a single [ApkInstaller] session, surfaced to the build/run UI. */
sealed interface InstallEvent {
    /** Copying the APK bytes into the install session. */
    object Preparing : InstallEvent

    /** The system is showing (or about to show) the confirm-install prompt to the user. */
    object AwaitingConfirmation : InstallEvent

    /** Install succeeded. [launched] is true if the app's launcher activity was auto-started. */
    data class Installed(val packageName: String?, val launched: Boolean) : InstallEvent

    data class Failed(val reason: String) : InstallEvent
}

/**
 * Pure mapping from a [PackageInstaller] status broadcast to a coarse outcome, kept separate from the
 * Android session plumbing so it is unit-testable. [ApkInstaller] turns these into [InstallEvent]s.
 */
internal object InstallStatusMapper {

    sealed interface Outcome {
        object NeedsUserAction : Outcome
        data class Success(val packageName: String?) : Outcome
        data class Failure(val message: String) : Outcome
    }

    fun map(status: Int, statusMessage: String?, packageName: String?): Outcome = when (status) {
        PackageInstaller.STATUS_PENDING_USER_ACTION -> Outcome.NeedsUserAction
        PackageInstaller.STATUS_SUCCESS -> Outcome.Success(packageName)
        else -> Outcome.Failure(describe(status, statusMessage))
    }

    /** A stable, human-readable reason for a non-success status. */
    fun describe(status: Int, statusMessage: String?): String {
        val base = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Install cancelled"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Install blocked by the device"
            PackageInstaller.STATUS_FAILURE_CONFLICT -> "Conflicts with an existing package"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "APK is incompatible with this device"
            PackageInstaller.STATUS_FAILURE_INVALID -> "APK is invalid or corrupt"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Not enough storage to install"
            else -> "Install failed"
        }
        return if (statusMessage.isNullOrBlank()) base else "$base: $statusMessage"
    }
}
