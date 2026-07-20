package com.ahmadkharfan.androidstudiolite.core.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat

enum class AslPermissionId {
    STORAGE,
    INSTALL_PACKAGES,
    NOTIFICATIONS,
    ;

    val domainId: String
        get() = when (this) {
            STORAGE -> "storage"
            INSTALL_PACKAGES -> "install"
            NOTIFICATIONS -> "notifications"
        }

    companion object {
        fun fromDomainId(id: String): AslPermissionId? = entries.firstOrNull { it.domainId == id }
    }
}

sealed interface AslPermissionRequest {
    data class Runtime(val manifestPermissions: List<String>) : AslPermissionRequest
    data class SettingsScreen(val intentAction: String) : AslPermissionRequest
}

data class AslPermissionDescriptor(
    val id: AslPermissionId,
    val title: String,
    val reason: String,
    val optional: Boolean,
    val request: AslPermissionRequest,
)

object AslPermissions {

    fun descriptors(): List<AslPermissionDescriptor> = buildList {
        add(
            AslPermissionDescriptor(
                id = AslPermissionId.STORAGE,
                title = "Storage access",
                reason = "Read and write your project files anywhere on device storage.",
                optional = false,
                request = if (isAtLeastR()) {
                    AslPermissionRequest.SettingsScreen(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                } else {
                    AslPermissionRequest.Runtime(
                        listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    )
                },
            ),
        )
        add(
            AslPermissionDescriptor(
                id = AslPermissionId.INSTALL_PACKAGES,
                title = "Install apps",
                reason = "Install the debug APKs Gradle builds, straight from the IDE.",
                optional = false,
                request = AslPermissionRequest.SettingsScreen(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES),
            ),
        )
        if (isAtLeastTiramisu()) {
            add(
                AslPermissionDescriptor(
                    id = AslPermissionId.NOTIFICATIONS,
                    title = "Notifications",
                    reason = "Get notified when a long build finishes in the background.",
                    optional = true,
                    request = AslPermissionRequest.Runtime(listOf(Manifest.permission.POST_NOTIFICATIONS)),
                ),
            )
        }
    }

    fun isGranted(context: Context, id: AslPermissionId): Boolean = when (id) {
        AslPermissionId.STORAGE -> isStorageGranted(context)
        AslPermissionId.INSTALL_PACKAGES -> canRequestPackageInstalls(context)
        AslPermissionId.NOTIFICATIONS -> !isAtLeastTiramisu() || checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
    }

    fun isStorageGranted(context: Context): Boolean {
        if (isAtLeastR()) return Environment.isExternalStorageManager()
        return checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) &&
            checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE)
    }

    fun canRequestPackageInstalls(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    fun allRequiredGranted(context: Context): Boolean =
        descriptors().filterNot { it.optional }.all { isGranted(context, it.id) }

    fun settingsIntent(context: Context, action: String): Intent =
        Intent(action).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }

    private fun checkSelfPermission(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun isAtLeastR(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
    private fun isAtLeastTiramisu(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
}
