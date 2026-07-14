package com.ahmadkharfan.androidstudiolite.core.tooling

import com.ahmadkharfan.androidstudiolite.tooling.proto.Incoming
import com.ahmadkharfan.androidstudiolite.tooling.proto.JsonValue
import com.ahmadkharfan.androidstudiolite.tooling.proto.Notification
import com.ahmadkharfan.androidstudiolite.tooling.proto.Request
import com.ahmadkharfan.androidstudiolite.tooling.proto.Response
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

/**
 * The app-side half of the JSON-RPC protocol. It doesn't own any transport: it turns a request into an
 * encoded line handed to [sendLine], and is [feed]-ed the lines coming back (relayed from the tooling
 * server via the foreground service). Responses are correlated by id to the awaiting caller; streamed
 * `event` notifications are published on [events]. This keeps all protocol knowledge on the app side
 * and out of the cross-process relay.
 */
class JsonRpcClient(private val sendLine: (String) -> Unit) {

    private val nextId = AtomicLong(1)
    private val pending = ConcurrentHashMap<Long, CompletableDeferred<Response>>()

    private val _events = MutableSharedFlow<Notification>(extraBufferCapacity = 256)
    val events: SharedFlow<Notification> = _events

    /** Sends a request and suspends until its response (or a transport failure) arrives. */
    suspend fun request(method: String, params: JsonValue.Obj): Response {
        val id = nextId.getAndIncrement()
        val deferred = CompletableDeferred<Response>()
        pending[id] = deferred
        try {
            sendLine(Request(id, method, params).encode())
        } catch (e: Throwable) {
            pending.remove(id)
            throw e
        }
        return deferred.await()
    }

    /** Routes one line from the server: a response completes its caller; an event is published. */
    fun feed(line: String) {
        val incoming = runCatching { Incoming.parse(line) }.getOrNull() ?: return
        when (incoming) {
            is Incoming.Resp -> pending.remove(incoming.response.id)?.complete(incoming.response)
            is Incoming.Event -> _events.tryEmit(incoming.notification)
            is Incoming.Req -> Unit // the app never receives requests
        }
    }

    /** Fails every in-flight request — called when the server process dies or the service unbinds. */
    fun failAll(cause: Throwable) {
        val snapshot = pending.values.toList()
        pending.clear()
        snapshot.forEach { it.completeExceptionally(cause) }
    }
}
