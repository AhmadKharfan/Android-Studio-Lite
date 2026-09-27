package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import java.io.File
import kotlinx.coroutines.flow.Flow

interface BuildSystem {

    /**
     * Whether a build could start right now, checked before one is admitted so that missing sign-in or
     * setup is reported up front instead of as a failed build. Providers that need nothing report
     * [BuildReadiness.Ready].
     */
    suspend fun readiness(): BuildReadiness = BuildReadiness.Ready

    suspend fun sync(projectRoot: File): ProjectModel

    fun build(request: BuildRequest): Flow<BuildEvent>

    fun attach(buildId: String, projectRoot: File): Flow<BuildEvent>

    fun cancel()
}

sealed interface BuildReadiness {

    data object Ready : BuildReadiness

    /** The provider needs the user to sign in, or to grant more access. */
    data class NeedsSignIn(val reason: String) : BuildReadiness

    /** The provider needs a one-time setup step before it can build. */
    data class NeedsSetup(val reason: String) : BuildReadiness

    /** The provider can't build at the moment for a reason the user can't fix from the app. */
    data class Unavailable(val reason: String) : BuildReadiness
}
