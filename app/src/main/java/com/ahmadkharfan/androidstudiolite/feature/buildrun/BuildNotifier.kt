package com.ahmadkharfan.androidstudiolite.feature.buildrun

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Posts a "build finished" notification so a long build that the user has switched away from still
 * reports its result (doc 10 §3.1). Uses the already-declared `POST_NOTIFICATIONS` permission and
 * no-ops gracefully when it hasn't been granted.
 */
class BuildNotifier(private val context: Context) {

    fun notifyFinished(projectName: String, success: Boolean, durationMillis: Long?) {
        if (!canPost()) return
        ensureChannel()
        val seconds = durationMillis?.let { it / 1000.0 }
        val title = if (success) "Build successful" else "Build failed"
        val text = buildString {
            append(projectName)
            if (seconds != null) append(" · ").append(String.format("%.1fs", seconds))
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
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
                NotificationChannel(CHANNEL_ID, "Builds", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Build completion notifications"
                },
            )
        }
    }

    private companion object {
        const val CHANNEL_ID = "asl_builds"
        const val NOTIFICATION_ID = 0x2011
    }
}
