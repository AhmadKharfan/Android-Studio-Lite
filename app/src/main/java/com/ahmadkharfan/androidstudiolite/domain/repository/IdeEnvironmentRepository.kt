package com.ahmadkharfan.androidstudiolite.domain.repository

import com.ahmadkharfan.androidstudiolite.domain.model.IdeEnvironmentState
import kotlinx.coroutines.flow.Flow

/**
 * Installs and tracks the real on-device build toolchain (JDK, Android SDK, Gradle) that the full
 * Gradle build needs — see docs/build-run/06-full-build-production-study.md. Every state change
 * (download progress, extraction, failure) is published on [observeState]; nothing here is simulated.
 */
interface IdeEnvironmentRepository {
    fun observeState(): Flow<IdeEnvironmentState>

    /** Re-reads on-disk marker files to determine what's already installed, without any network I/O. */
    suspend fun refresh()

    /** Installs every not-yet-installed component, in order, streaming progress via [observeState]. */
    suspend fun installAll()

    /** Cancels an in-flight install; partially-downloaded files are left for a resumable retry. */
    fun cancelInstall()
}
