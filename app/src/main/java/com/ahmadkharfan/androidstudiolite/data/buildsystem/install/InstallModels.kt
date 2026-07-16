package com.ahmadkharfan.androidstudiolite.data.buildsystem.install

import android.content.pm.PackageInstaller

/** Progress of a single [ApkInstaller] session, surfaced to the build/run UI. */
sealed interface InstallEvent {
    /** Copying the APK bytes into the install session. */
    object Preparing : InstallEvent

    /** The system is showing (or about to show) the confirm-install prompt to the user. */
    object AwaitingConfirmation : InstallEvent

    /** Install succeeded. [launched] is true if the app's launcher activity was auto-started. */
    data class Installed(val packageName: String?, val launched: Boolean) : InstallEvent

    /**
     * The APK can't be installed over what's already on the device — in practice because the two are
     * signed with different certificates (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Distinct from
     * [Failed] because it is recoverable: uninstalling the existing package (losing ITS data) and
     * reinstalling works. [packageName] is the conflicting package when known — either the one the
     * system reported or the applicationId the caller passed to `install`.
     *
     * Since the build worker signs every APK with one fixed debug keystore, this should now only
     * happen for apps still installed from a build made before that fix.
     */
    data class Conflict(val packageName: String?, val reason: String) : InstallEvent

    data class Failed(val reason: String) : InstallEvent
}

/** Progress of an [ApkInstaller.uninstall], the recovery step for an [InstallEvent.Conflict]. */
sealed interface UninstallEvent {
    object Uninstalling : UninstallEvent

    /** The system is showing (or about to show) the confirm-uninstall prompt to the user. */
    object AwaitingConfirmation : UninstallEvent

    object Uninstalled : UninstallEvent

    data class Failed(val reason: String) : UninstallEvent
}

/**
 * Pure mapping from a [PackageInstaller] status broadcast to a coarse outcome, kept separate from the
 * Android session plumbing so it is unit-testable. [ApkInstaller] turns these into [InstallEvent]s.
 */
internal object InstallStatusMapper {

    sealed interface Outcome {
        object NeedsUserAction : Outcome
        data class Success(val packageName: String?) : Outcome

        /** Recoverable by uninstalling [packageName] first; see [InstallEvent.Conflict]. */
        data class Conflict(val packageName: String?, val message: String) : Outcome
        data class Failure(val message: String) : Outcome
    }

    fun map(status: Int, statusMessage: String?, packageName: String?): Outcome = when (status) {
        PackageInstaller.STATUS_PENDING_USER_ACTION -> Outcome.NeedsUserAction
        PackageInstaller.STATUS_SUCCESS -> Outcome.Success(packageName)
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            Outcome.Conflict(packageName, describe(status, statusMessage))
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
