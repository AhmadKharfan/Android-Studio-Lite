package com.ahmadkharfan.androidstudiolite.data.remote

internal object BuildErrorMessages {
    fun userFacingBuildError(t: Throwable): String {
        val chain = generateSequence(t) { it.cause }.toList()
        chain.filterIsInstance<RemoteException>().forEach { remoteErrorMessage(it)?.let { message -> return message } }
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
        val messages = chain.mapNotNull { it.message?.takeIf(String::isNotBlank) }
        messages.forEach { networkErrorMessage(it)?.let { message -> return message } }
        return messages.firstOrNull().orEmpty().ifBlank {
            "Build failed. Check your internet connection and try again."
        }
    }

    private fun remoteErrorMessage(error: RemoteException): String? {
        val code = error.code?.lowercase().orEmpty()
        val message = error.message.takeIf { it.isNotBlank() && !it.matches(Regex("""HTTP \d+""")) }
        return when {
            code == "quota_exceeded" || "quota" in error.message.lowercase() ||
                "build time" in error.message.lowercase() ->
                message
                    ?: "You've used today's build time for this device. Quota resets at midnight UTC. Try again tomorrow."
            error.httpStatus == 429 || code == "rate_limited" ->
                message ?: "Too many builds right now. Wait a moment and try again."
            else -> null
        }
    }

    private fun networkErrorMessage(message: String): String? {
        val lower = message.lowercase()
        return when {
            "unable to resolve host" in lower ||
                "failed to connect" in lower ||
                "network is unreachable" in lower ||
                "software caused connection abort" in lower ||
                "connection refused" in lower ||
                lower == "network error" ->
                "You're offline or can't reach the build server. Check your internet connection and try again."
            message.contains("PROTOCOL_ERROR", ignoreCase = true) ||
                message.contains("stream was reset", ignoreCase = true) ->
                "Connection to the build server was interrupted. Check your internet and try again."
            else -> null
        }
    }
}
