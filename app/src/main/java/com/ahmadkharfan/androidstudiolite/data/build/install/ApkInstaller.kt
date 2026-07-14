package com.ahmadkharfan.androidstudiolite.data.build.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * Own implementation of on-device APK installation on public Android APIs (no reference to
 * android-code-studio's GPL `ApkInstaller`). Streams an APK into a [PackageInstaller.Session], commits
 * it, drives the system user-confirmation prompt, and — on success — can auto-launch the freshly
 * installed app's MAIN/LAUNCHER activity.
 *
 * Requires `REQUEST_INSTALL_PACKAGES` (declared) and the user having granted "install unknown apps".
 */
class ApkInstaller(private val context: Context) {

    /**
     * Installs [apk] and emits progress. When [autoLaunch] is true and the install succeeds, the
     * installed app is launched. The flow completes after a terminal [InstallEvent].
     */
    fun install(apk: File, autoLaunch: Boolean): Flow<InstallEvent> = callbackFlow {
        if (!apk.isFile) {
            trySend(InstallEvent.Failed("APK not found: ${apk.name}"))
            close()
            return@callbackFlow
        }

        val action = "${context.packageName}.INSTALL_STATUS.${SESSION_COUNTER.incrementAndGet()}"
        val installer = context.packageManager.packageInstaller
        var sessionId = -1

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                val pkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
                when (val outcome = InstallStatusMapper.map(status, message, pkg)) {
                    is InstallStatusMapper.Outcome.NeedsUserAction -> {
                        trySend(InstallEvent.AwaitingConfirmation)
                        val confirm = intent.confirmationIntent()
                        if (confirm != null) {
                            context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } else {
                            trySend(InstallEvent.Failed("System did not provide a confirmation prompt"))
                            close()
                        }
                    }

                    is InstallStatusMapper.Outcome.Success -> {
                        val launched = if (autoLaunch) launchApp(outcome.packageName) else false
                        trySend(InstallEvent.Installed(outcome.packageName, launched))
                        close()
                    }

                    is InstallStatusMapper.Outcome.Failure -> {
                        trySend(InstallEvent.Failed(outcome.message))
                        close()
                    }
                }
            }
        }
        registerInstallReceiver(receiver, action)

        try {
            trySend(InstallEvent.Preparing)
            sessionId = writeSession(installer, apk)
            installer.openSession(sessionId).use { session ->
                session.commit(buildStatusSender(action).intentSender)
            }
        } catch (e: Exception) {
            trySend(InstallEvent.Failed(e.message ?: "Could not start install"))
            close()
        }

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
            if (sessionId >= 0) runCatching { installer.abandonSession(sessionId) }
        }
    }

    /** Creates a session, copies the APK bytes in, and returns the session id. */
    private fun writeSession(installer: PackageInstaller, apk: File): Int {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(null)
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
        }
        return sessionId
    }

    private fun buildStatusSender(action: String): PendingIntent {
        val intent = Intent(action).setPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }

    private fun launchApp(packageName: String?): Boolean {
        if (packageName == null) return false
        val launch = context.packageManager.getLaunchIntentForPackage(packageName) ?: return false
        return runCatching {
            context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    }

    private fun registerInstallReceiver(receiver: BroadcastReceiver, action: String) {
        val filter = IntentFilter(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    private companion object {
        const val REQUEST_CODE = 0xA5
        val SESSION_COUNTER = AtomicInteger(0)
    }
}

@Suppress("DEPRECATION")
private fun Intent.confirmationIntent(): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    } else {
        getParcelableExtra(Intent.EXTRA_INTENT)
    }
