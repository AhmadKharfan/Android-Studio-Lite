package com.ahmadkharfan.androidstudiolite.data.remote

import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildErrorMessagesTest {

    @Test
    fun quotaCodeProducesQuotaMessage() {
        val error = RemoteException(403, "quota_exceeded", "HTTP 403")

        assertEquals(QUOTA_MESSAGE, BuildErrorMessages.userFacingBuildError(error))
    }

    @Test
    fun quotaWordingProducesUsableRemoteMessage() {
        val error = RemoteException(403, null, "Daily build time quota exhausted")

        assertEquals(
            "Daily build time quota exhausted",
            BuildErrorMessages.userFacingBuildError(error),
        )
    }

    @Test
    fun rateLimitCodeProducesRateLimitMessage() {
        val error = RemoteException(503, "rate_limited", "HTTP 503")

        assertEquals(RATE_LIMIT_MESSAGE, BuildErrorMessages.userFacingBuildError(error))
    }

    @Test
    fun usableRemoteMessageIsPreferredOverCannedMessage() {
        val error = RemoteException(429, null, "Retry after 30 seconds")

        assertEquals("Retry after 30 seconds", BuildErrorMessages.userFacingBuildError(error))
    }

    @Test
    fun bareHttp429MessageIsNotTreatedAsUsable() {
        val error = RemoteException(429, null, "HTTP 429")

        assertEquals(RATE_LIMIT_MESSAGE, BuildErrorMessages.userFacingBuildError(error))
    }

    @Test
    fun unknownHostAnywhereInCauseChainProducesNetworkMessage() {
        val error = IllegalStateException("outer", UnknownHostException("build.example"))

        assertEquals(UNKNOWN_HOST_MESSAGE, BuildErrorMessages.userFacingBuildError(error))
    }

    @Test
    fun matchingRemoteExceptionIsFoundSeveralLevelsDeep() {
        val error = IllegalStateException(
            "outer",
            IllegalArgumentException(
                "middle",
                RemoteException(403, "quota_exceeded", "HTTP 403"),
            ),
        )

        assertEquals(QUOTA_MESSAGE, BuildErrorMessages.userFacingBuildError(error))
    }

    @Test
    fun unrecognisedExceptionWithoutMessageProducesGenericMessage() {
        assertEquals(
            GENERIC_MESSAGE,
            BuildErrorMessages.userFacingBuildError(IllegalStateException()),
        )
    }

    private companion object {
        const val QUOTA_MESSAGE =
            "You've used today's build time for this device. Quota resets at midnight UTC. Try again tomorrow."
        const val RATE_LIMIT_MESSAGE = "Too many builds right now. Wait a moment and try again."
        const val UNKNOWN_HOST_MESSAGE =
            "You're offline or DNS failed. Check your internet connection and try again."
        const val GENERIC_MESSAGE = "Build failed. Check your internet connection and try again."
    }
}
