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

class ApkInstaller(private val context: Context) {

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


            if (sessionId >= 0 && (!committed || !terminalReached)) {
                runCatching { installer.abandonSession(sessionId) }
            }
            if (!terminalReached) {
                PendingInstallPrompt.clear()
                InstallPromptNotifier(context).cancel()
            }
        }
    }

    fun uninstall(packageName: String): Flow<UninstallEvent> = callbackFlow {
        val action = "${context.packageName}.UNINSTALL_STATUS.${SESSION_COUNTER.incrementAndGet()}"
        val installer = context.packageManager.packageInstaller

        val receiver = statusReceiver(
            onNeedsUserAction = { trySend(UninstallEvent.AwaitingConfirmation) },
            onNoPrompt = { trySend(UninstallEvent.Failed("System did not provide a confirmation prompt")); close() },
            onSuccess = { trySend(UninstallEvent.Uninstalled); close() },

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


                        if (isAppInForeground()) {
                            val confirmToShow = PendingInstallPrompt.claimForLaunch()
                            if (confirmToShow != null) {
                                runCatching { context.startActivity(confirmToShow) }
                            }
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
