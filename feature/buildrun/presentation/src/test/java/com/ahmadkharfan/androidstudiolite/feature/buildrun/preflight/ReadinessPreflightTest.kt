package com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildReadiness
import com.ahmadkharfan.androidstudiolite.feature.buildrun.api.preflight.PreflightSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReadinessPreflightTest {

    @Test
    fun `a ready provider adds nothing`() {
        assertNull(readinessWarning(BuildReadiness.Ready))
    }

    @Test
    fun `every not-ready state blocks the build and carries the provider's reason`() {
        listOf(
            BuildReadiness.NeedsSignIn("Sign in to GitHub"),
            BuildReadiness.NeedsSetup("Create the build repository"),
            BuildReadiness.Unavailable("Actions is disabled"),
        ).forEach { readiness ->
            val warning = readinessWarning(readiness)

            assertEquals(PreflightSeverity.BLOCKER, warning?.severity)
            assertEquals(
                when (readiness) {
                    is BuildReadiness.NeedsSignIn -> readiness.reason
                    is BuildReadiness.NeedsSetup -> readiness.reason
                    is BuildReadiness.Unavailable -> readiness.reason
                    BuildReadiness.Ready -> null
                },
                warning?.detail,
            )
        }
    }
}
