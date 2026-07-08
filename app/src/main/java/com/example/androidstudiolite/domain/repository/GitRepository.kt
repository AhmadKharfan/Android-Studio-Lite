package com.example.androidstudiolite.domain.repository

import com.example.androidstudiolite.domain.model.GitDiffLine
import com.example.androidstudiolite.domain.model.GitState
import kotlinx.coroutines.flow.Flow

interface GitRepository {
    fun observeState(): Flow<GitState>
    suspend fun getDiff(path: String): List<GitDiffLine>
    suspend fun setCommitMessage(message: String)
    suspend fun commit()
}
