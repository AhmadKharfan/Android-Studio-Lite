package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfig
import kotlinx.coroutines.flow.Flow

/** App-owned Git identity. It deliberately does not consult the process user's home directory. */
interface GitAuthorStore {
    fun observe(): Flow<GitAuthorConfig?>
    suspend fun get(): GitAuthorConfig?
    suspend fun set(config: GitAuthorConfig?)
}
