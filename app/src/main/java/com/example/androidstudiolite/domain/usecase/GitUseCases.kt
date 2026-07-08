package com.example.androidstudiolite.domain.usecase

import com.example.androidstudiolite.domain.model.GitDiffLine
import com.example.androidstudiolite.domain.model.GitState
import com.example.androidstudiolite.domain.repository.GitRepository
import kotlinx.coroutines.flow.Flow

class ObserveGitStateUseCase(private val repository: GitRepository) {
    operator fun invoke(): Flow<GitState> = repository.observeState()
}

class GetGitDiffUseCase(private val repository: GitRepository) {
    suspend operator fun invoke(path: String): List<GitDiffLine> = repository.getDiff(path)
}

class SetGitCommitMessageUseCase(private val repository: GitRepository) {
    suspend operator fun invoke(message: String) = repository.setCommitMessage(message)
}

class CommitGitChangesUseCase(private val repository: GitRepository) {
    suspend operator fun invoke() = repository.commit()
}
