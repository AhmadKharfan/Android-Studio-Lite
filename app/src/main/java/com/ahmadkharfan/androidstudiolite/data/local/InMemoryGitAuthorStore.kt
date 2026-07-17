package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitAuthorConfig
import com.ahmadkharfan.androidstudiolite.domain.repository.GitAuthorStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Lightweight default for JVM callers; production replaces it with [DataStoreGitAuthorStore]. */
class InMemoryGitAuthorStore(initial: GitAuthorConfig? = null) : GitAuthorStore {
    private val state = MutableStateFlow(initial)
    override fun observe(): Flow<GitAuthorConfig?> = state
    override suspend fun get(): GitAuthorConfig? = state.value
    override suspend fun set(config: GitAuthorConfig?) { state.value = config }
}
