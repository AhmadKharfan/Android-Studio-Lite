package com.ahmadkharfan.androidstudiolite.data.buildsystem.install

import android.Manifest
import android.annotation.SuppressLint
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
import com.ahmadkharfan.androidstudiolite.feature.buildrun.R

class InstallPromptNotifier(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun notifyReady(
        apkLabel: String,
        requestToken: String,
        confirmationIntent: Intent,
        projectId: String = "",
    ) {
        if (!canPost()) return
        ensureChannel()
        val trampoline = Intent(context, InstallConfirmActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_NO_ANIMATION
            putExtra(InstallConfirmActivity.EXTRA_APK_LABEL, apkLabel)
            putExtra(InstallConfirmActivity.EXTRA_REQUEST_TOKEN, requestToken)
            putExtra(InstallConfirmActivity.EXTRA_CONFIRMATION_INTENT, confirmationIntent)
            if (projectId.isNotBlank()) {
                putExtra(InstallConfirmActivity.EXTRA_PROJECT_ID, projectId)
            }
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            requestToken.hashCode(),
            trampoline,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.build_notification_success_install))
            .setContentText(apkLabel)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.install_notification_body, apkLabel)),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(requestToken.hashCode(), notification) }
    }

    fun cancel(requestToken: String) {
        NotificationManagerCompat.from(context).cancel(requestToken.hashCode())
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
                    context.getString(R.string.install_notification_channel),
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = context.getString(R.string.install_notification_channel_description)
                    enableVibration(true)
                    setShowBadge(true)
                },
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "asl_install_prompt"
    }
}
