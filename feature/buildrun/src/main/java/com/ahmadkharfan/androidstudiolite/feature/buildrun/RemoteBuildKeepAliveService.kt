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
import androidx.core.app.ServiceCompat
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

class RemoteBuildKeepAliveService : Service() {

    private val coordinator: BuildRunCoordinator by inject()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var executionJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                coordinator.cancel()
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
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
            ACTION_EXECUTE -> {
                val operationId = intent.getStringExtra(EXTRA_OPERATION_ID) ?: return START_NOT_STICKY
                promote(
                    projectName = intent.getStringExtra(EXTRA_PROJECT_NAME).orEmpty().ifBlank { "Project" },
                    projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty(),
                    progress = "Preparing…",
                )
                if (executionJob?.isActive == true) return START_REDELIVER_INTENT
                val root = intent.getStringExtra(EXTRA_PROJECT_ROOT)?.let(::File) ?: return START_NOT_STICKY
                val kind = runCatching {
                    BuildKind.valueOf(intent.getStringExtra(EXTRA_BUILD_KIND).orEmpty())
                }.getOrDefault(BuildKind.ASSEMBLE)
                val request = BuildRequest(
                    projectRoot = root,
                    modulePath = intent.getStringExtra(EXTRA_MODULE_PATH).orEmpty().ifBlank { ":app" },
                    variantName = intent.getStringExtra(EXTRA_VARIANT).orEmpty().ifBlank { "debug" },
                    kind = kind,
                    operationId = operationId,
                )
                val meta = BuildClientMeta(
                    projectId = intent.getStringExtra(EXTRA_PROJECT_ID).orEmpty(),
                    projectName = intent.getStringExtra(EXTRA_PROJECT_NAME).orEmpty(),
                    installAfterSuccess = intent.getBooleanExtra(EXTRA_INSTALL, false),
                    autoLaunchAfterInstall = intent.getBooleanExtra(EXTRA_AUTO_LAUNCH, true),
                )
                executionJob = serviceScope.launch {
                    try {
                        coordinator.execute(
                            operationId,
                            request,
                            meta,
                            attachBuildId = intent.getStringExtra(EXTRA_ATTACH_BUILD_ID),
                        )
                    } finally {
                        stopForegroundCompat()
                        stopSelf()
                    }
                }
                return START_REDELIVER_INTENT
            }
            else -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
    }

    private fun promote(projectName: String, projectId: String, progress: String?) {
        ensureChannel()
        val notification = buildOngoingNotification(projectName, projectId, progress)
        ServiceCompat.startForeground(
            this,
            ONGOING_NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        coordinator.cancel()
        stopForegroundCompat()
        stopSelf(startId)
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
                ?.putExtra("com.ahmadkharfan.androidstudiolite.OPEN_PROJECT_ID", projectId)
                ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val cancelIntent = PendingIntent.getService(
            this,
            ONGOING_NOTIFICATION_ID,
            Intent(this, RemoteBuildKeepAliveService::class.java).setAction(ACTION_CANCEL),
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
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
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
        const val ACTION_STOP = "com.ahmadkharfan.androidstudiolite.action.BUILD_KEEPALIVE_STOP"
        const val ACTION_CANCEL = "com.ahmadkharfan.androidstudiolite.action.BUILD_CANCEL"
        const val ACTION_UPDATE_PROGRESS = "com.ahmadkharfan.androidstudiolite.action.BUILD_KEEPALIVE_PROGRESS"
        const val ACTION_EXECUTE = "com.ahmadkharfan.androidstudiolite.action.BUILD_EXECUTE"
        const val EXTRA_PROJECT_NAME = "project_name"
        const val EXTRA_PROJECT_ID = "project_id"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_OPERATION_ID = "operation_id"
        const val EXTRA_PROJECT_ROOT = "project_root"
        const val EXTRA_MODULE_PATH = "module_path"
        const val EXTRA_VARIANT = "variant"
        const val EXTRA_BUILD_KIND = "build_kind"
        const val EXTRA_INSTALL = "install"
        const val EXTRA_AUTO_LAUNCH = "auto_launch"
        const val EXTRA_ATTACH_BUILD_ID = "attach_build_id"
        const val CHANNEL_ID = "asl_build_progress"
        const val ONGOING_NOTIFICATION_ID = 0x2010

        fun startExecution(
            context: Context,
            operationId: String,
            request: BuildRequest,
            meta: BuildClientMeta,
            attachBuildId: String? = null,
        ) {
            val intent = Intent(context, RemoteBuildKeepAliveService::class.java).apply {
                action = ACTION_EXECUTE
                putExtra(EXTRA_OPERATION_ID, operationId)
                putExtra(EXTRA_PROJECT_ID, meta.projectId)
                putExtra(EXTRA_PROJECT_NAME, meta.projectName)
                putExtra(EXTRA_PROJECT_ROOT, request.projectRoot.absolutePath)
                putExtra(EXTRA_MODULE_PATH, request.modulePath)
                putExtra(EXTRA_VARIANT, request.variantName)
                putExtra(EXTRA_BUILD_KIND, request.kind.name)
                putExtra(EXTRA_INSTALL, meta.installAfterSuccess)
                putExtra(EXTRA_AUTO_LAUNCH, meta.autoLaunchAfterInstall)
                if (!attachBuildId.isNullOrBlank()) putExtra(EXTRA_ATTACH_BUILD_ID, attachBuildId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
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
            runCatching { context.stopService(intent) }
        }
    }
}
