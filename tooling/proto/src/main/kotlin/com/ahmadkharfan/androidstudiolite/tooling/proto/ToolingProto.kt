package com.ahmadkharfan.androidstudiolite.tooling.proto

/**
 * The JSON-RPC protocol shared by the app (client) and the on-device tooling server.
 *
 * Wire format: newline-delimited JSON over stdio — one JSON object per line, UTF-8. stdin/stdout
 * carry the protocol; the server logs to stderr so it never corrupts the stream. Three envelope
 * shapes:
 *  - request       `{"id":N,"method":"…","params":{…}}`   (client → server, expects a response)
 *  - response      `{"id":N,"result":{…}}` or `{"id":N,"error":{"message":"…","stack":"…"}}`
 *  - notification  `{"method":"event","params":{"type":"…",…}}`  (server → client, no id, no reply)
 *
 * Requests are `sync`, `build`, `cancel`, `shutdown`. While a `build`/`sync` runs, the server streams
 * `event` notifications (progress/log/task/problem) that the client maps onto the shared `BuildEvent`
 * flow; the terminal `result` of the originating request reports overall success.
 *
 * This file is the single source of truth for the wire shapes. It is cleanroom — written fresh against
 * the protocol described above, sharing no code with any GPL reference project.
 */
object ToolingProto {

    const val VERSION = 1

    /** JSON-RPC methods (client → server). */
    object Method {
        const val SYNC = "sync"
        const val BUILD = "build"
        const val CANCEL = "cancel"
        const val SHUTDOWN = "shutdown"
    }

    /** The single notification method; the concrete kind is in the payload's `type`. */
    const val EVENT = "event"

    /** `event` payload discriminators (server → client). */
    object EventType {
        const val PROGRESS = "progress"
        const val LOG = "log"
        const val TASK_STARTED = "taskStarted"
        const val TASK_FINISHED = "taskFinished"
        const val PROBLEM = "problem"
    }
}

// --------------------------------------------------------------------------- envelopes

/** A client→server call awaiting a [Response] with the same [id]. */
data class Request(val id: Long, val method: String, val params: JsonValue.Obj) {
    fun encode(): String = JsonValue.obj(
        "id" to JsonValue.of(id),
        "method" to JsonValue.of(method),
        "params" to params,
    ).encode()

    companion object {
        fun decode(obj: JsonValue.Obj): Request =
            Request(
                id = obj.long("id") ?: error("request missing id"),
                method = obj.string("method") ?: error("request missing method"),
                params = obj.obj("params") ?: JsonValue.obj(),
            )
    }
}

/** A server→client reply carrying either [result] or [error], keyed to a [Request.id]. */
data class Response(val id: Long, val result: JsonValue.Obj?, val error: RpcError?) {
    fun encode(): String = JsonValue.obj(
        "id" to JsonValue.of(id),
        *if (error != null) arrayOf("error" to error.toJson())
        else arrayOf("result" to (result ?: JsonValue.obj())),
    ).encode()

    companion object {
        fun ok(id: Long, result: JsonValue.Obj): Response = Response(id, result, null)
        fun failed(id: Long, error: RpcError): Response = Response(id, null, error)

        fun decode(obj: JsonValue.Obj): Response =
            Response(
                id = obj.long("id") ?: error("response missing id"),
                result = obj.obj("result"),
                error = obj.obj("error")?.let { RpcError.fromJson(it) },
            )
    }
}

data class RpcError(val message: String, val stack: String? = null) {
    fun toJson(): JsonValue.Obj = JsonValue.obj(
        "message" to JsonValue.of(message),
        "stack" to JsonValue.of(stack),
    )

    companion object {
        fun fromJson(obj: JsonValue.Obj): RpcError =
            RpcError(obj.string("message") ?: "unknown error", obj.string("stack"))
    }
}

/** A server→client streaming event (no id, no reply). */
data class Notification(val type: String, val params: JsonValue.Obj) {
    fun encode(): String = JsonValue.obj(
        "method" to JsonValue.of(ToolingProto.EVENT),
        "params" to JsonValue.Obj(linkedMapOf("type" to JsonValue.of(type)) + params.fields),
    ).encode()

    companion object {
        fun decode(paramsObj: JsonValue.Obj): Notification =
            Notification(
                type = paramsObj.string("type") ?: error("event missing type"),
                params = paramsObj,
            )
    }
}

/**
 * Classifies one line off the wire without the caller having to know the envelope shape:
 * a message with an `id` is a request or response; one whose method is `event` is a notification.
 */
sealed interface Incoming {
    data class Req(val request: Request) : Incoming
    data class Resp(val response: Response) : Incoming
    data class Event(val notification: Notification) : Incoming

    companion object {
        fun parse(line: String): Incoming {
            val obj = JsonValue.parse(line) as? JsonValue.Obj
                ?: error("expected a JSON object, got: $line")
            return when {
                obj["method"]?.let { (it as? JsonValue.Str)?.value } == ToolingProto.EVENT ->
                    Event(Notification.decode(obj.obj("params") ?: JsonValue.obj()))
                obj.fields.containsKey("method") -> Req(Request.decode(obj))
                obj.fields.containsKey("id") -> Resp(Response.decode(obj))
                else -> error("unrecognized message: $line")
            }
        }
    }
}
