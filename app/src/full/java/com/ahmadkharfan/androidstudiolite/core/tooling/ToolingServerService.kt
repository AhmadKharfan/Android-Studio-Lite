package com.ahmadkharfan.androidstudiolite.core.tooling

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.util.Log
import androidx.core.app.NotificationCompat
import java.util.concurrent.CopyOnWriteArraySet

/**
 * Hosts the tooling-server subprocess in a separate `:tooling` android process and keeps it (and the
 * build it's running) alive as a foreground service while the user navigates away. It is a transparent
 * relay: registered clients send JSON-RPC request lines via [ToolingIpc.MSG_RPC], which are written to
 * the server's stdin; every line the server prints is broadcast back as [ToolingIpc.MSG_LINE].
 *
 * The subprocess lives here (not in the app's UI process) so its memory pressure — a JVM plus the
 * Gradle daemon — can't take the UI down, and so the foreground guarantee actually covers the process
 * doing the work. If the subprocess dies mid-session it is restarted a bounded number of times and
 * clients are told via [ToolingIpc.MSG_SERVER_CRASHED] so their in-flight requests fail fast.
 */
class ToolingServerService : Service() {

    private lateinit var launcher: ToolingServerLauncher
    private lateinit var handlerThread: HandlerThread
    private lateinit var handler: Handler
    private lateinit var messenger: Messenger

    private val clients = CopyOnWriteArraySet<Messenger>()

    @Volatile private var transport: ProcessTransport? = null
    @Volatile private var restarts = 0

    override fun onCreate() {
        super.onCreate()
        launcher = ToolingServerLauncher(this)
        handlerThread = HandlerThread("tooling-service").apply { start() }
        handler = Handler(handlerThread.looper) { handleMessage(it); true }
        messenger = Messenger(handler)
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Started explicitly around a build so the foreground guarantee holds even without a binding.
        goForeground()
        return START_NOT_STICKY
    }

    private fun handleMessage(msg: Message) {
        when (msg.what) {
            ToolingIpc.MSG_REGISTER -> msg.replyTo?.let { clients.add(it) }
            ToolingIpc.MSG_UNREGISTER -> msg.replyTo?.let { clients.remove(it) }
            ToolingIpc.MSG_RPC -> {
                ensureServer()
                msg.data?.getString(ToolingIpc.KEY_LINE)?.let { transport?.writeLine(it) }
            }
            ToolingIpc.MSG_START_FOREGROUND -> goForeground()
            ToolingIpc.MSG_STOP_FOREGROUND -> stopForeground(STOP_FOREGROUND_REMOVE)
            else -> Log.w(TAG, "unknown message ${msg.what}")
        }
    }

    @Synchronized
    private fun ensureServer() {
        if (transport?.isAlive == true) return
        val t = launcher.createTransport()
        t.onLine = { line -> broadcast(lineMessage(line)) }
        t.onExit = { code -> onServerExit(code) }
        transport = t
        t.start()
        Log.i(TAG, "Tooling server started")
    }

    private fun onServerExit(code: Int) {
        Log.w(TAG, "Tooling server exited with code $code")
        broadcast(crashMessage("tooling server exited (code $code)"))
        transport = null
        if (restarts < MAX_RESTARTS) {
            restarts++
            Log.i(TAG, "Restarting tooling server (attempt $restarts/$MAX_RESTARTS)")
            ensureServer()
        } else {
            Log.e(TAG, "Tooling server exceeded restart budget; giving up")
        }
    }

    private fun broadcast(message: Message) {
        val dead = ArrayList<Messenger>()
        for (client in clients) {
            try {
                client.send(Message.obtain(message))
            } catch (_: RemoteException) {
                dead += client
            }
        }
        clients.removeAll(dead.toSet())
    }

    private fun lineMessage(line: String): Message = Message.obtain(null, ToolingIpc.MSG_LINE).apply {
        data = Bundle().apply { putString(ToolingIpc.KEY_LINE, line) }
    }

    private fun crashMessage(reason: String): Message = Message.obtain(null, ToolingIpc.MSG_SERVER_CRASHED).apply {
        data = Bundle().apply { putString(ToolingIpc.KEY_MESSAGE, reason) }
    }

    private fun goForeground() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AndroidStudioLite")
            .setContentText("Gradle tooling server running")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Gradle tooling", NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        transport?.stop()
        transport = null
        handlerThread.quitSafely()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "tooling-server"
        private const val CHANNEL_ID = "tooling-server"
        private const val NOTIFICATION_ID = 4210
        private const val MAX_RESTARTS = 3

        fun start(context: Context) {
            val intent = Intent(context, ToolingServerService::class.java)
            context.startForegroundService(intent)
        }
    }
}
