package com.ahmadkharfan.androidstudiolite.data.remote

data class ServerSettings(
    val baseUrl: String,
    val deviceToken: String?,
) {
    val isConfigured: Boolean get() = baseUrl.isNotBlank()
    val isRegistered: Boolean get() = !deviceToken.isNullOrBlank()
}
