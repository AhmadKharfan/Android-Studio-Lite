package com.ahmadkharfan.androidstudiolite.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildErrorMessagesRegressionTest {

    @Test
    fun `nested connection failure is not masked by an outer message`() {
        val error = IllegalStateException(
            "outer",
            IllegalStateException("connection refused"),
        )

        assertEquals(
            "You're offline or can't reach the build server. Check your internet connection and try again.",
            BuildErrorMessages.userFacingBuildError(error),
        )
    }
}
