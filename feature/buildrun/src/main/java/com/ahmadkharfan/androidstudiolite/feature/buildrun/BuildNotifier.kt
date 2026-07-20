package com.ahmadkharfan.androidstudiolite.feature.buildrun

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
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.InstallConfirmActivity

class BuildNotifier(private val context: Context) {

    fun notifyFinished(
        projectName: String,
        success: Boolean,
        durationMillis: Long?,
        projectId: String = "",
        installFollows: Boolean = false,
    ) {
        if (!canPost()) return
        ensureChannel()

        if (!installFollows) {
            NotificationManagerCompat.from(context).cancel(RemoteBuildKeepAliveService.ONGOING_NOTIFICATION_ID)
        }

        val seconds = durationMillis?.let { it / 1000.0 }
        val title = when {
            success && installFollows -> "Build successful — tap to install"
            success -> "Build successful"
            else -> "Build failed"
        }
        val text = buildString {
            append(projectName)
            if (seconds != null) append(" · ").append(String.format("%.1fs", seconds))
        }


        val contentIntent = if (success && installFollows) {
            PendingIntent.getActivity(
                context,
                projectId.hashCode().xor(NOTIFICATION_ID),
                Intent(context, InstallConfirmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(InstallConfirmActivity.EXTRA_PROJECT_ID, projectId)
                    putExtra(InstallConfirmActivity.EXTRA_APK_LABEL, projectName)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        } else {
            PendingIntent.getActivity(
                context,
                projectId.hashCode().xor(NOTIFICATION_ID),
                context.packageManager.getLaunchIntentForPackage(context.packageName)
                    ?.apply { putExtra("open_project_id", projectId) }
                    ?: Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    fun canPost(): Boolean =
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
                    "Build results",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Build completion alerts"
                    enableVibration(true)
                    setShowBadge(true)
                },
            )
        }

        runCatching { manager.deleteNotificationChannel(LEGACY_CHANNEL_ID) }
    }

    private companion object {
        const val CHANNEL_ID = "asl_build_results"
        const val LEGACY_CHANNEL_ID = "asl_builds"
        const val NOTIFICATION_ID = 0x2011
    }
}
