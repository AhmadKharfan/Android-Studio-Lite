package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import kotlinx.coroutines.flow.Flow

/**
 * A build that has been admitted and is expected to still be running, persisted so the app can
 * re-attach to it after process death.
 */
data class ActiveBuild(
    val buildId: String,
    val operationId: String = buildId,
    val projectId: String,
    val projectRootPath: String,
    val projectName: String,
    val installAfterSuccess: Boolean,
    val autoLaunchAfterInstall: Boolean = true,
    val startedAtEpochMs: Long,
    val modulePath: String = "",
    val variantName: String = "",
    val kind: String = "ASSEMBLE",
    val taskPath: String? = null,
    val buildType: String? = null,
)

interface ActiveBuildRepository {
    fun observe(): Flow<ActiveBuild?>
    suspend fun get(): ActiveBuild?
    suspend fun save(build: ActiveBuild)

    /** Clears the stored build, or only [buildId] when given, so a stale writer cannot clobber a newer build. */
    suspend fun clear(buildId: String? = null)
}
