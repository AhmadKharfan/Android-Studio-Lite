package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfig
import kotlinx.coroutines.flow.Flow

interface GitAuthorStore {
    fun observe(): Flow<GitAuthorConfig?>
    suspend fun get(): GitAuthorConfig?
    suspend fun set(config: GitAuthorConfig?)
}
