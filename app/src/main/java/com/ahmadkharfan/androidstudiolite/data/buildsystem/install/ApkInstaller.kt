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
        if (!apk.isFile || apk.length() == 0L) {
            trySend(InstallEvent.Failed("APK not found: ${apk.name}"))
            close()
            return@callbackFlow
        }

        val action = "${context.packageName}.INSTALL_STATUS.${SESSION_COUNTER.incrementAndGet()}"
        val installer = context.packageManager.packageInstaller
        var sessionId = -1
        var committed = false
        var terminalReached = false

        val receiver = statusReceiver(
            apkLabel = apk.name,
            onNeedsUserAction = { trySend(InstallEvent.AwaitingConfirmation) },
            onNoPrompt = {
                terminalReached = true
                trySend(InstallEvent.Failed("System did not provide a confirmation prompt"))
                close()
            },
            onSuccess = { pkg ->
                terminalReached = true
                PendingInstallPrompt.clear()
                InstallPromptNotifier(context).cancel()
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
                terminalReached = true
                PendingInstallPrompt.clear()
                InstallPromptNotifier(context).cancel()
                trySend(InstallEvent.Conflict(pkg ?: applicationId, message))
                close()
            },
            onFailure = { message ->
                terminalReached = true
                PendingInstallPrompt.clear()
                InstallPromptNotifier(context).cancel()
                trySend(InstallEvent.Failed(message))
                close()
            },
        )
        registerInstallReceiver(receiver, action)

        try {
            trySend(InstallEvent.Preparing)
            // Drop stale sessions from a previous Run that left "pending user action" / abandoned mid-way.
            // Confirming those while creating a new session causes INSTALL_FAILED_INTERNAL_ERROR:
            // "Session files in use".
            abandonStaleSessions(installer)
            sessionId = createAndCommitSession(installer, apk, applicationId, action)
            committed = true
        } catch (e: Exception) {
            terminalReached = true
            trySend(InstallEvent.Failed(e.message ?: "Could not start install"))
            close()
        }

        awaitClose {
            runCatching { context.unregisterReceiver(receiver) }
            // Never abandon a committed session that already finished — and don't abandon while the
            // system install UI is still using the session (that yields "Session files in use").
            // Only abandon if we never committed, or the user cancelled before a terminal status.
            if (sessionId >= 0 && (!committed || !terminalReached)) {
                runCatching { installer.abandonSession(sessionId) }
            }
            if (!terminalReached) {
                PendingInstallPrompt.clear()
                InstallPromptNotifier(context).cancel()
            }
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
            installer.uninstall(packageName, buildStatusSender(action, requestCode = action.hashCode()).intentSender)
        } catch (e: Exception) {
            trySend(UninstallEvent.Failed(e.message ?: "Could not start uninstall"))
            close()
        }

        awaitClose { runCatching { context.unregisterReceiver(receiver) } }
    }

    /**
     * The shared status-broadcast plumbing behind [install] and [uninstall]: maps the broadcast to an
     * outcome and, for the user-action case, launches the system prompt (or a heads-up notification
     * when background activity launch is blocked).
     */
    private fun statusReceiver(
        apkLabel: String = "app",
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
                        val labeled = confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        PendingInstallPrompt.hold(labeled, apkLabel)
                        // Single path: foreground → show the system sheet once; background → one
                        // heads-up notification. Doing both caused two popups / two notifications.
                        if (isAppInForeground()) {
                            PendingInstallPrompt.recordLaunchAttempt()
                            runCatching { context.startActivity(Intent(labeled)) }
                        } else {
                            InstallPromptNotifier(context).notifyReady(apkLabel)
                        }
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

    /**
     * Writes the APK and commits in one open session (Android's recommended pattern). Split
     * open-write-close then re-open-commit left some OEMs with "Session files in use" on confirm.
     */
    private fun createAndCommitSession(
        installer: PackageInstaller,
        apk: File,
        applicationId: String?,
        action: String,
    ): Int {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (!applicationId.isNullOrBlank()) {
            params.setAppPackageName(applicationId)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("base.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            session.commit(buildStatusSender(action, requestCode = sessionId).intentSender)
        }
        return sessionId
    }

    private fun abandonStaleSessions(installer: PackageInstaller) {
        for (info in installer.mySessions) {
            runCatching { installer.abandonSession(info.sessionId) }
        }
        PendingInstallPrompt.clear()
        InstallPromptNotifier(context).cancel()
    }

    private fun buildStatusSender(action: String, requestCode: Int): PendingIntent {
        val intent = Intent(action).setPackage(context.packageName)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
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

    /** True when this app is in the foreground — used to avoid dual install UI / notifications. */
    private fun isAppInForeground(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? android.app.ActivityManager
            ?: return false
        val pkg = context.packageName
        return am.runningAppProcesses.orEmpty().any {
            it.processName == pkg &&
                it.importance == android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }

    private companion object {
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
