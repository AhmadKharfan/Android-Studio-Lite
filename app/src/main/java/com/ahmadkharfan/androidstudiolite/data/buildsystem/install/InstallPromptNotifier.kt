package com.ahmadkharfan.androidstudiolite.data.buildsystem.install

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Holds a PackageInstaller confirmation [Intent] until the user actually confirms (or the install
 * fails/cancels). Android often lets `startActivity` from a background receiver return without
 * showing UI — so we never trust that path alone; a heads-up notification / foreground trampoline
 * is the reliable way to present the install sheet.
 */
object PendingInstallPrompt {
    @Volatile
    private var confirmation: Intent? = null

    @Volatile
    private var label: String = "app"

    @Volatile
    private var lastLaunchAttemptAtMs: Long = 0L

    fun hold(confirmationIntent: Intent, apkLabel: String) {
        confirmation = Intent(confirmationIntent)
        label = apkLabel.ifBlank { "app" }
        lastLaunchAttemptAtMs = 0L
    }

    /** Snapshot for launching without clearing — clear only on terminal install success/failure. */
    fun peek(): Intent? = confirmation?.let { Intent(it) }

    fun hasPending(): Boolean = confirmation != null

    fun apkLabel(): String = label

    fun clear() {
        confirmation = null
        lastLaunchAttemptAtMs = 0L
    }

    /**
     * True when we should auto-present from MainActivity resume. Debounced so we don't double-open
     * the system sheet (which caused "Session files in use").
     */
    fun shouldAutoPresent(nowMs: Long = System.currentTimeMillis()): Boolean {
        if (confirmation == null) return false
        if (nowMs - lastLaunchAttemptAtMs < LAUNCH_DEBOUNCE_MS) return false
        return true
    }

    fun recordLaunchAttempt(nowMs: Long = System.currentTimeMillis()) {
        lastLaunchAttemptAtMs = nowMs
    }

    private const val LAUNCH_DEBOUNCE_MS = 2_000L
}

/**
 * Heads-up "tap to install" notification — the reliable path when the IDE was backgrounded.
 */
class InstallPromptNotifier(private val context: Context) {

    fun notifyReady(apkLabel: String, projectId: String = "") {
        if (!canPost()) return
        ensureChannel()
        val trampoline = Intent(context, InstallConfirmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            putExtra(InstallConfirmActivity.EXTRA_APK_LABEL, apkLabel)
            if (projectId.isNotBlank()) {
                putExtra(InstallConfirmActivity.EXTRA_PROJECT_ID, projectId)
            }
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID,
            trampoline,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Build successful — tap to install")
            .setContentText(apkLabel)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Tap to open the install prompt for $apkLabel."),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun cancel() {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun canPost(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Install prompts",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Shows when a built APK is ready to install"
                    enableVibration(true)
                    setShowBadge(true)
                },
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "asl_install_prompt"
        const val NOTIFICATION_ID = 0x2012
    }
}
