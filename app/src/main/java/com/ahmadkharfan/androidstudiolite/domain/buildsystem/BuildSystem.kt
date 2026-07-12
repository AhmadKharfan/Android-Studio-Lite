package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * The single build abstraction both flavors implement:
 *  - full  → GradleToolingBuildSystem (client to the on-device tooling-server process)
 *  - play  → InProcessBuildSystem (own incremental task pipeline on ART)
 *
 * All UI (build output, variants, problems, run) targets only this interface; the flavor-specific
 * Koin module selects the binding.
 */
interface BuildSystem {

    /** Resolves the project's structure: modules, variants, source sets, dependencies. */
    suspend fun sync(projectRoot: File): ProjectModel

    /** Runs a build, emitting progress, logs, structured problems, and produced artifacts. */
    fun build(request: BuildRequest): Flow<BuildEvent>

    /** Cancels the in-flight sync or build, if any. */
    fun cancel()
}
