package com.ahmadkharfan.androidstudiolite.domain.repository

import kotlinx.coroutines.flow.Flow

interface GitHubDeviceAuthenticator {

    val isConfigured: Boolean

    fun authenticate(): Flow<GitHubDeviceAuthState>
}

sealed interface GitHubDeviceAuthState {

    data object RequestingCode : GitHubDeviceAuthState

    data class AwaitingAuthorization(
        val userCode: String,
        val verificationUri: String,
    ) : GitHubDeviceAuthState

    data class Success(val login: String?) : GitHubDeviceAuthState

    data class Error(val message: String) : GitHubDeviceAuthState
}
