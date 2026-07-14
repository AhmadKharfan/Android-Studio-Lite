package com.ahmadkharfan.androidstudiolite.core.tooling

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import com.ahmadkharfan.androidstudiolite.tooling.proto.BuildParams
import com.ahmadkharfan.androidstudiolite.tooling.proto.JsonValue
import com.ahmadkharfan.androidstudiolite.tooling.proto.Notification
import com.ahmadkharfan.androidstudiolite.tooling.proto.Response
import com.ahmadkharfan.androidstudiolite.tooling.proto.SyncParams
import com.ahmadkharfan.androidstudiolite.tooling.proto.ToolingProto
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-process handle to the tooling server. It binds the [ToolingServerService] (in the `:tooling`
 * process), then bridges the Messenger relay to a [JsonRpcClient]: outgoing request lines go to the
 * service as [ToolingIpc.MSG_RPC]; incoming [ToolingIpc.MSG_LINE] lines are fed back for correlation,
 * and a [ToolingIpc.MSG_SERVER_CRASHED] fails all in-flight requests.
 */
class ToolingServerClient(private val context: Context) {

    private val jsonRpc = JsonRpcClient(sendLine = ::sendRpc)
    val events: SharedFlow<Notification> get() = jsonRpc.events

    private val incoming = Handler(Looper.getMainLooper()) { msg ->
        when (msg.what) {
            ToolingIpc.MSG_LINE -> msg.data?.getString(ToolingIpc.KEY_LINE)?.let(jsonRpc::feed)
            ToolingIpc.MSG_SERVER_CRASHED ->
                jsonRpc.failAll(IllegalStateException(msg.data?.getString(ToolingIpc.KEY_MESSAGE) ?: "server crashed"))
        }
        true
    }
    private val replyMessenger = Messenger(incoming)

    private val connectMutex = Mutex()
    @Volatile private var serviceMessenger: Messenger? = null
    @Volatile private var connected: CompletableDeferred<Unit>? = null

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val service = Messenger(binder)
            serviceMessenger = service
            service.send(Message.obtain(null, ToolingIpc.MSG_REGISTER).apply { replyTo = replyMessenger })
            connected?.complete(Unit)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            serviceMessenger = null
            jsonRpc.failAll(IllegalStateException("tooling service disconnected"))
        }
    }

    /** Binds (and foreground-starts) the service if not already connected. */
    suspend fun ensureConnected() {
        if (serviceMessenger != null) return
        connectMutex.withLock {
            if (serviceMessenger != null) return
            val deferred = CompletableDeferred<Unit>()
            connected = deferred
            ToolingServerService.start(context)
            val intent = Intent(context, ToolingServerService::class.java)
            context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            deferred.await()
        }
    }

    suspend fun sync(params: SyncParams): Response =
        jsonRpc.request(ToolingProto.Method.SYNC, params.toJson())

    suspend fun build(params: BuildParams): Response =
        jsonRpc.request(ToolingProto.Method.BUILD, params.toJson())

    suspend fun cancel(): Response =
        jsonRpc.request(ToolingProto.Method.CANCEL, JsonValue.obj())

    fun startForeground() = sendControl(ToolingIpc.MSG_START_FOREGROUND)

    fun stopForeground() = sendControl(ToolingIpc.MSG_STOP_FOREGROUND)

    private fun sendControl(what: Int) {
        runCatching { serviceMessenger?.send(Message.obtain(null, what).apply { replyTo = replyMessenger }) }
    }

    private fun sendRpc(line: String) {
        val service = serviceMessenger ?: throw IllegalStateException("tooling service not connected")
        service.send(
            Message.obtain(null, ToolingIpc.MSG_RPC).apply {
                replyTo = replyMessenger
                data = Bundle().apply { putString(ToolingIpc.KEY_LINE, line) }
            },
        )
    }
}
