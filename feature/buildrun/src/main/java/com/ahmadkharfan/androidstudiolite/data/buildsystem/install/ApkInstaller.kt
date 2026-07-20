package com.ahmadkharfan.androidstudiolite.data.buildsystem.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.launch

class ApkInstaller(private val context: Context) {
    @Volatile private var currentSessionId: Int = -1

    fun install(
        apk: File,
        applicationId: String?,
        autoLaunch: Boolean,
        requestToken: String = UUID.randomUUID().toString(),
    ): Flow<InstallEvent> = callbackFlow {
        if (!apk.isFile || apk.length() == 0L) {
            trySend(InstallEvent.Failed("APK not found: ${apk.name}"))
            close()
            return@callbackFlow
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            trySend(InstallEvent.Failed("Allow installs from this app in Android settings, then run again"))
            close()
            return@callbackFlow
        }

        val installer = context.packageManager.packageInstaller
        var sessionId = -1
        var committed = false

        val receiver = statusReceiver(
            apkLabel = apk.name,
            onNeedsUserAction = { trySend(InstallEvent.AwaitingConfirmation) },
            onNoPrompt = {
                trySend(InstallEvent.Failed("System did not provide a confirmation prompt"))
                close()
            },
            onSuccess = { pkg ->
                InstallPromptNotifier(context).cancel(requestToken)
                launch {
                    val launched = if (autoLaunch) launchAppWithRetry(pkg ?: applicationId) else false
                    trySend(InstallEvent.Installed(pkg ?: applicationId, launched))
                    close()
                }
            },
            onConflict = { pkg, message ->
                InstallPromptNotifier(context).cancel(requestToken)
                trySend(InstallEvent.Conflict(pkg ?: applicationId, message))
                close()
            },
            onFailure = { message ->
                InstallPromptNotifier(context).cancel(requestToken)
                trySend(InstallEvent.Failed(message))
                close()
            },
        )
        val relayJob = launch(start = CoroutineStart.UNDISPATCHED) {
            InstallStatusRelay.flow
                .filter { it.getStringExtra(InstallStatusReceiver.EXTRA_REQUEST_TOKEN) == requestToken }
                .collect { receiver.onReceive(context, it) }
        }

        try {
            trySend(InstallEvent.Preparing)
            sessionId = createAndCommitSession(installer, apk, applicationId, requestToken)
            currentSessionId = sessionId
            committed = true
        } catch (e: Exception) {
            trySend(InstallEvent.Failed(e.message ?: "Could not start install"))
            close()
        }

        awaitClose {
            relayJob.cancel()
            if (currentSessionId == sessionId) currentSessionId = -1
            if (sessionId >= 0 && !committed) {
                runCatching { installer.abandonSession(sessionId) }
            }
        }
    }

    fun cancelActiveInstall() {
        val sessionId = currentSessionId
        if (sessionId < 0) return
        currentSessionId = -1
        runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
    }

    fun uninstall(packageName: String): Flow<UninstallEvent> = callbackFlow {
        val requestToken = UUID.randomUUID().toString()
        val installer = context.packageManager.packageInstaller

        val receiver = statusReceiver(
            onNeedsUserAction = { trySend(UninstallEvent.AwaitingConfirmation) },
            onNoPrompt = { trySend(UninstallEvent.Failed("System did not provide a confirmation prompt")); close() },
            onSuccess = { trySend(UninstallEvent.Uninstalled); close() },

            onConflict = { _, message -> trySend(UninstallEvent.Failed(message)); close() },
            onFailure = { message -> trySend(UninstallEvent.Failed(message)); close() },
        )
        val relayJob = launch(start = CoroutineStart.UNDISPATCHED) {
            InstallStatusRelay.flow
                .filter { it.getStringExtra(InstallStatusReceiver.EXTRA_REQUEST_TOKEN) == requestToken }
                .collect { receiver.onReceive(context, it) }
        }

        try {
            trySend(UninstallEvent.Uninstalling)
            installer.uninstall(
                packageName,
                buildStatusSender(requestToken, packageName, requestCode = requestToken.hashCode()).intentSender,
            )
        } catch (e: Exception) {
            trySend(UninstallEvent.Failed(e.message ?: "Could not start uninstall"))
            close()
        }

        awaitClose { relayJob.cancel() }
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
                    if (intent.confirmationIntent() == null) onNoPrompt()
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
        requestToken: String,
    ): Int {
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        if (!applicationId.isNullOrBlank()) {
            params.setAppPackageName(applicationId)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(buildStatusSender(requestToken, apk.name, requestCode = sessionId).intentSender)
            }
        } catch (error: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw error
        }
        return sessionId
    }

    private fun buildStatusSender(requestToken: String, apkLabel: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, InstallStatusReceiver::class.java).apply {
            putExtra(InstallStatusReceiver.EXTRA_REQUEST_TOKEN, requestToken)
            putExtra(InstallStatusReceiver.EXTRA_APK_LABEL, apkLabel)
        }
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

    private companion object {
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
