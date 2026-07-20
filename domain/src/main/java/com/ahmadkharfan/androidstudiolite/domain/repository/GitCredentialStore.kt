package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import kotlinx.coroutines.flow.Flow

interface GitCredentialStore {

    fun credentialsForUrl(url: String): GitCredentials?

    fun credentialsForHost(host: String): GitCredentials?

    fun hasCredentials(host: String): Boolean

    fun save(host: String, credentials: GitCredentials)

    fun clear(host: String)

    val changes: Flow<Unit>
}
