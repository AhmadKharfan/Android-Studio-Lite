package com.ahmadkharfan.androidstudiolite.data.remote

internal object BuildErrorMessages {
    fun userFacingBuildError(t: Throwable): String {
        for (err in generateSequence(t) { it.cause }) {
            if (err is RemoteException) {
                val code = err.code?.lowercase().orEmpty()
                val msg = err.message.takeIf { it.isNotBlank() && !it.matches(Regex("""HTTP \d+""")) }
                when {
                    code == "quota_exceeded" || "quota" in err.message.lowercase() ||
                        "build time" in err.message.lowercase() ->
                        return msg
                            ?: "You've used today's build time for this device. Quota resets at midnight UTC. Try again tomorrow."
                    err.httpStatus == 429 || code == "rate_limited" ->
                        return msg
                            ?: "Too many builds right now. Wait a moment and try again."
                }
            }
        }

        val chain = generateSequence(t) { it.cause }.toList()
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
        val message = chain.mapNotNull { it.message?.takeIf(String::isNotBlank) }.firstOrNull().orEmpty()
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
            message.isNotBlank() -> message
            else -> "Build failed. Check your internet connection and try again."
        }
    }
}
