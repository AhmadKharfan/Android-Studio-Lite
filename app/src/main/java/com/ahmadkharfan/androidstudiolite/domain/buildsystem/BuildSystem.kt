package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import java.io.File
import kotlinx.coroutines.flow.Flow

/**
 * The single build abstraction the app targets. Builds run server-side: A2's `RemoteBuildSystem`
 * implements this against the remote build API (REST + a WebSocket [BuildEvent] stream). Until then a
 * temporary [com.ahmadkharfan.androidstudiolite.data.buildsystem.FakeBuildSystem] is bound.
 *
 * All UI (build output, variants, problems, run) targets only this interface; the Koin graph selects
 * the binding.
 */
interface BuildSystem {

    /** Resolves the project's structure: modules, variants, source sets, dependencies. */
    suspend fun sync(projectRoot: File): ProjectModel

    /** Runs a build, emitting progress, logs, structured problems, and produced artifacts. */
    fun build(request: BuildRequest): Flow<BuildEvent>

    /** Cancels the in-flight sync or build, if any. */
    fun cancel()
}
