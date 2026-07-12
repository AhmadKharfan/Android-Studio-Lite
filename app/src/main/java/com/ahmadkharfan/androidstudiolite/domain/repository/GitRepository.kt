package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffLine
import com.ahmadkharfan.androidstudiolite.domain.model.GitState
import kotlinx.coroutines.flow.Flow

interface GitRepository {
    fun observeState(): Flow<GitState>
    suspend fun getDiff(path: String): List<GitDiffLine>
    suspend fun setCommitMessage(message: String)
    suspend fun commit()
}
