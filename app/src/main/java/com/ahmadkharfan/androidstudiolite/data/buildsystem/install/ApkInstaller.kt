package com.ahmadkharfan.androidstudiolite.data.buildsystem.install

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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch

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
     *
     * [applicationId] is the package the APK declares, used only to name the conflicting package in
     * [InstallEvent.Conflict] when the system doesn't report one — the caller needs it to offer an
     * uninstall-and-retry. Pass null when it isn't known.
     */
    fun install(apk: File, applicationId: String?, autoLaunch: Boolean): Flow<InstallEvent> = callbackFlow {
        if (!apk.isFile) {
            trySend(InstallEvent.Failed("APK not found: ${apk.name}"))
            close()
            return@callbackFlow
        }

        val action = "${context.packageName}.INSTALL_STATUS.${SESSION_COUNTER.incrementAndGet()}"
        val installer = context.packageManager.packageInstaller
        var sessionId = -1

        val receiver = statusReceiver(
            onNeedsUserAction = { trySend(InstallEvent.AwaitingConfirmation) },
            onNoPrompt = { trySend(InstallEvent.Failed("System did not provide a confirmation prompt")); close() },
            onSuccess = { pkg ->
                // Launch off the broadcast thread with a short retry: right after commit the package's
                // MAIN/LAUNCHER activity is often not yet queryable, so getLaunchIntentForPackage would
                // return null on the first try and auto-launch would silently no-op.
                launch {
                    val launched = if (autoLaunch) launchAppWithRetry(pkg ?: applicationId) else false
                    trySend(InstallEvent.Installed(pkg ?: applicationId, launched))
                    close()
                }
            },
            onConflict = { pkg, message ->
                trySend(InstallEvent.Conflict(pkg ?: applicationId, message))
                close()
            },
            onFailure = { message -> trySend(InstallEvent.Failed(message)); close() },
        )
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

    /**
     * Uninstalls [packageName], driving the system's confirmation prompt the same way [install] does.
     * Used to clear a package whose signature conflicts with a new build ([InstallEvent.Conflict]);
     * it destroys that app's data, so only call it once the user has agreed.
     */
    fun uninstall(packageName: String): Flow<UninstallEvent> = callbackFlow {
        val action = "${context.packageName}.UNINSTALL_STATUS.${SESSION_COUNTER.incrementAndGet()}"
        val installer = context.packageManager.packageInstaller

        val receiver = statusReceiver(
            onNeedsUserAction = { trySend(UninstallEvent.AwaitingConfirmation) },
            onNoPrompt = { trySend(UninstallEvent.Failed("System did not provide a confirmation prompt")); close() },
            onSuccess = { trySend(UninstallEvent.Uninstalled); close() },
            // The uninstall path never reports CONFLICT; fold it in with the other failures.
            onConflict = { _, message -> trySend(UninstallEvent.Failed(message)); close() },
            onFailure = { message -> trySend(UninstallEvent.Failed(message)); close() },
        )
        registerInstallReceiver(receiver, action)

        try {
            trySend(UninstallEvent.Uninstalling)
            installer.uninstall(packageName, buildStatusSender(action).intentSender)
        } catch (e: Exception) {
            trySend(UninstallEvent.Failed(e.message ?: "Could not start uninstall"))
            close()
        }

        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    /**
     * The shared status-broadcast plumbing behind [install] and [uninstall]: maps the broadcast to an
     * outcome and, for the user-action case, launches the system prompt.
     */
    private fun statusReceiver(
        onNeedsUserAction: () -> Unit,
        onNoPrompt: () -> Unit,
        onSuccess: (packageName: String?) -> Unit,
        onConflict: (packageName: String?, message: String) -> Unit,
        onFailure: (message: String) -> Unit,
    ) = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
            val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            val pkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
            when (val outcome = InstallStatusMapper.map(status, message, pkg)) {
                is InstallStatusMapper.Outcome.NeedsUserAction -> {
                    onNeedsUserAction()
                    val confirm = intent.confirmationIntent()
                    if (confirm != null) {
                        context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    } else {
                        onNoPrompt()
                    }
                }

                is InstallStatusMapper.Outcome.Success -> onSuccess(outcome.packageName)
                is InstallStatusMapper.Outcome.Conflict -> onConflict(outcome.packageName, outcome.message)
                is InstallStatusMapper.Outcome.Failure -> onFailure(outcome.message)
            }
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

    /**
     * Launches [packageName]'s MAIN/LAUNCHER activity, retrying briefly because the launcher entry is
     * frequently not resolvable for a few hundred ms after the install commits. Returns true once the
     * app is started, false if it never became launchable within the window.
     */
    private suspend fun launchAppWithRetry(packageName: String?): Boolean {
        if (packageName == null) return false
        repeat(LAUNCH_RETRIES) { attempt ->
            val launch = context.packageManager.getLaunchIntentForPackage(packageName)
            if (launch != null) {
                val started = runCatching {
                    context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isSuccess
                if (started) return true
            }
            if (attempt < LAUNCH_RETRIES - 1) delay(LAUNCH_RETRY_DELAY_MS)
        }
        return false
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

        /** ~2s total (10 × 200ms) to cover the post-commit window before the launcher entry resolves. */
        const val LAUNCH_RETRIES = 10
        const val LAUNCH_RETRY_DELAY_MS = 200L
    }
}

@Suppress("DEPRECATION")
private fun Intent.confirmationIntent(): Intent? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
    } else {
        getParcelableExtra(Intent.EXTRA_INTENT)
    }
