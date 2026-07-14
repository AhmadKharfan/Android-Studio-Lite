package com.ahmadkharfan.androidstudiolite.data.remote

/** Persisted configuration for the server-side build backend. */
data class ServerSettings(
    /** Base URL of the control plane, e.g. `https://build.example.com`. No trailing slash. */
    val baseUrl: String,
    /** The anonymous device token minted by `POST /v1/devices`, or null until registered. */
    val deviceToken: String?,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()
    val isRegistered: Boolean get() = !deviceToken.isNullOrBlank()
}
