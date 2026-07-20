package com.ahmadkharfan.androidstudiolite.data.buildsystem.install

import android.content.pm.PackageInstaller

sealed interface InstallEvent {
    object Preparing : InstallEvent

    object AwaitingConfirmation : InstallEvent

    data class Installed(val packageName: String?, val launched: Boolean) : InstallEvent

    data class Conflict(val packageName: String?, val reason: String) : InstallEvent

    data class Failed(val reason: String) : InstallEvent
}

sealed interface UninstallEvent {
    object Uninstalling : UninstallEvent

    object AwaitingConfirmation : UninstallEvent

    object Uninstalled : UninstallEvent

    data class Failed(val reason: String) : UninstallEvent
}

internal object InstallStatusMapper {

    sealed interface Outcome {
        object NeedsUserAction : Outcome
        data class Success(val packageName: String?) : Outcome

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
