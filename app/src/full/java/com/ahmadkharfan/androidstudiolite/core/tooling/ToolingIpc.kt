package com.ahmadkharfan.androidstudiolite.core.tooling

/**
 * The tiny Messenger protocol between the app process and the tooling-server foreground service,
 * which lives in a separate `:tooling` android process. Because the JSON-RPC protocol with the actual
 * server is already newline-delimited JSON, the service is a thin relay: it owns the java subprocess
 * and shuttles whole JSON-RPC *lines* across the binder in both directions. Correlation of responses
 * and event routing happens app-side in [JsonRpcClient]; the service never parses the protocol.
 */
object ToolingIpc {

    // ---- app → service ----
    /** Register the sender's `replyTo` Messenger to receive server lines. */
    const val MSG_REGISTER = 1
    /** Forward [KEY_LINE] to the server's stdin (a JSON-RPC request line). */
    const val MSG_RPC = 2
    /** Promote the service to the foreground for the duration of a build. */
    const val MSG_START_FOREGROUND = 3
    /** Drop foreground once a build finishes. */
    const val MSG_STOP_FOREGROUND = 4
    /** Unregister the sender's `replyTo`. */
    const val MSG_UNREGISTER = 5

    // ---- service → app ----
    /** One line of server stdout ([KEY_LINE]): a JSON-RPC response or event. */
    const val MSG_LINE = 100
    /** The server process died; every in-flight request must fail. [KEY_MESSAGE] carries a reason. */
    const val MSG_SERVER_CRASHED = 101

    const val KEY_LINE = "line"
    const val KEY_MESSAGE = "message"
}
