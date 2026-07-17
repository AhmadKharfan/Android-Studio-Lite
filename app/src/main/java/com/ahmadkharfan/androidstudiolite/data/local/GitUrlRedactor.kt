package com.ahmadkharfan.androidstudiolite.data.local

import java.net.URI

/** Removes HTTP(S) user-info so credentials never escape through models or exception messages. */
object GitUrlRedactor {
    private val embeddedUserInfo = Regex("""(?i)(https?://)[^/@\s]+@""")

    fun stripUserInfo(url: String): String = runCatching {
        val uri = URI(url.trim())
        if (uri.userInfo == null) return@runCatching url.trim()
        URI(uri.scheme, null, uri.host, uri.port, uri.path, uri.query, uri.fragment).toString()
    }.getOrDefault(url.trim().replace(embeddedUserInfo, "$1"))

    fun redact(message: String?, url: String? = null): String {
        var result = message.orEmpty().replace(embeddedUserInfo, "$1")
        if (!url.isNullOrBlank()) result = result.replace(url, stripUserInfo(url))
        return result.ifBlank { "Git operation failed" }
    }

    fun hasUserInfo(url: String): Boolean = runCatching { URI(url.trim()).userInfo != null }
        .getOrDefault(embeddedUserInfo.containsMatchIn(url))
}
