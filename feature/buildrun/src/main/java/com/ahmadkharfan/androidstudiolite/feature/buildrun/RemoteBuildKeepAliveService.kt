package com.ahmadkharfan.androidstudiolite.feature.buildrun

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

class RemoteBuildKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_PROGRESS -> {
                val projectName = intent.getStringExtra(EXTRA_PROJECT_NAME).orEmpty()
                val projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
                val progress = intent.getStringExtra(EXTRA_PROGRESS)
                val notification = buildOngoingNotification(projectName, projectId, progress)
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(ONGOING_NOTIFICATION_ID, notification)
                return START_STICKY
            }
            else -> {
                val projectName = intent?.getStringExtra(EXTRA_PROJECT_NAME).orEmpty().ifBlank { "Project" }
                val projectId = intent?.getStringExtra(EXTRA_PROJECT_ID).orEmpty()
                val progress = intent?.getStringExtra(EXTRA_PROGRESS)
                ensureChannel()
                val notification = buildOngoingNotification(projectName, projectId, progress)
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        startForeground(
                            ONGOING_NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        startForeground(ONGOING_NOTIFICATION_ID, notification)
                    }
                } catch (_: Exception) {


                    runCatching {
                        getSystemService(NotificationManager::class.java)
                            ?.notify(ONGOING_NOTIFICATION_ID, notification)
                    }
                }
                return START_STICKY
            }
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
    }

    private fun buildOngoingNotification(
        projectName: String,
        projectId: String,
        progress: String?,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            projectId.hashCode(),
            packageManager.getLaunchIntentForPackage(packageName)
                ?.putExtra("open_project_id", projectId)
                ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("Building…")
            .setContentText(progress?.takeIf { it.isNotBlank() } ?: projectName)
            .setSubText(projectName.takeIf { !progress.isNullOrBlank() })
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(contentIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(0, 0, true)
            .build()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Build progress",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Ongoing remote builds"
                },
            )
        }
    }

    companion object {
        const val ACTION_START = "com.ahmadkharfan.androidstudiolite.action.BUILD_KEEPALIVE_START"
        const val ACTION_STOP = "com.ahmadkharfan.androidstudiolite.action.BUILD_KEEPALIVE_STOP"
        const val ACTION_UPDATE_PROGRESS = "com.ahmadkharfan.androidstudiolite.action.BUILD_KEEPALIVE_PROGRESS"
        const val EXTRA_PROJECT_NAME = "project_name"
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_PROGRESS = "progress"
        const val CHANNEL_ID = "asl_build_progress"
        const val ONGOING_NOTIFICATION_ID = 0x2010

        fun startBuilding(context: Context, projectId: String, projectName: String, progress: String? = null) {
            val intent = Intent(context, RemoteBuildKeepAliveService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_PROJECT_ID, projectId)
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_PROGRESS, progress)
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun updateProgress(context: Context, projectId: String, projectName: String, progress: String?) {
            val intent = Intent(context, RemoteBuildKeepAliveService::class.java).apply {
                action = ACTION_UPDATE_PROGRESS
                putExtra(EXTRA_PROJECT_ID, projectId)
                putExtra(EXTRA_PROJECT_NAME, projectName)
                putExtra(EXTRA_PROGRESS, progress)
            }
            runCatching { context.startService(intent) }
        }

        fun stopBuilding(context: Context) {
            val intent = Intent(context, RemoteBuildKeepAliveService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching { context.startService(intent) }
        }
    }
}
