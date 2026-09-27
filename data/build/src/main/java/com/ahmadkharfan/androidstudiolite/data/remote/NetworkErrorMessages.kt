package com.ahmadkharfan.androidstudiolite.data.remote

import java.io.IOException

internal const val SERVER_UNREACHABLE_MESSAGE =
    "Can't reach the build server right now. Your internet connection is working, so the server may be " +
        "unavailable. Try again later."

/**
 * The user-facing text for a transport failure talking to the build server.
 *
 * @param deviceOnline whether the device had validated internet at the time, or null when unknown. A
 *   failure to resolve or connect while online means the server itself is unreachable, so telling the
 *   user to check their connection would send them after the wrong problem.
 */
internal fun networkErrorMessage(e: IOException, deviceOnline: Boolean?): String {
    val chain = generateSequence<Throwable>(e) { it.cause }.toList()
    if (deviceOnline == true && isServerUnreachable(chain)) return SERVER_UNREACHABLE_MESSAGE
    for (err in chain) {
        when (err) {
            is java.net.UnknownHostException ->
                return "You're offline or DNS failed. Check your internet connection and try again."
            is java.net.ConnectException ->
                return "Can't reach the build server. Check your internet connection and try again."
            is java.net.NoRouteToHostException ->
                return "No network route to the build server. Check your internet connection."
            is java.net.SocketTimeoutException ->
                return "Build server timed out. Check your internet connection and try again."
        }
    }
    val message = e.message.orEmpty().lowercase()
    return when {
        isUnreachableMessage(message) || "network is unreachable" in message ->
            "You're offline or can't reach the build server. Check your internet connection and try again."
        e.message.isNullOrBlank() ->
            "Network error. Check your internet connection and try again."
        else -> e.message!!
    }
}

private fun isServerUnreachable(chain: List<Throwable>): Boolean =
    chain.any {
        it is java.net.UnknownHostException ||
            it is java.net.ConnectException ||
            it is java.net.NoRouteToHostException
    } || isUnreachableMessage(chain.first().message.orEmpty().lowercase())

private fun isUnreachableMessage(lowercaseMessage: String): Boolean =
    "unable to resolve host" in lowercaseMessage ||
        "failed to connect" in lowercaseMessage ||
        "connection refused" in lowercaseMessage
