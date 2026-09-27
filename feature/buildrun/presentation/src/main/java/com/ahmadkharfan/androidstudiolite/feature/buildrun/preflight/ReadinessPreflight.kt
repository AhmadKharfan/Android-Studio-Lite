package com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildReadiness
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.preflight.PreflightSeverity
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.preflight.PreflightWarning

/** A blocking preflight warning when the build provider can't start a build, or null when it can. */
internal fun readinessWarning(readiness: BuildReadiness): PreflightWarning? = when (readiness) {
    BuildReadiness.Ready -> null
    is BuildReadiness.NeedsSignIn ->
        PreflightWarning(PreflightSeverity.BLOCKER, "Sign in to build", readiness.reason)
    is BuildReadiness.NeedsSetup ->
        PreflightWarning(PreflightSeverity.BLOCKER, "Build setup required", readiness.reason)
    is BuildReadiness.Unavailable ->
        PreflightWarning(PreflightSeverity.BLOCKER, "Build service unavailable", readiness.reason)
}
