package com.ahmadkharfan.androidstudiolite.data.remote

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkErrorMessagesTest {

    @Test
    fun `unresolvable server while online blames the server, not the connection`() {
        val error = UnknownHostException("Unable to resolve host \"build.example\"")

        assertEquals(SERVER_UNREACHABLE_MESSAGE, networkErrorMessage(error, deviceOnline = true))
    }

    @Test
    fun `refused connection while online blames the server`() {
        assertEquals(
            SERVER_UNREACHABLE_MESSAGE,
            networkErrorMessage(IOException("wrapped", ConnectException("refused")), deviceOnline = true),
        )
    }

    @Test
    fun `resolution failure reported only in the message while online blames the server`() {
        assertEquals(
            SERVER_UNREACHABLE_MESSAGE,
            networkErrorMessage(IOException("Unable to resolve host build.example"), deviceOnline = true),
        )
    }

    @Test
    fun `unresolvable server while offline still points at the connection`() {
        assertEquals(
            "You're offline or DNS failed. Check your internet connection and try again.",
            networkErrorMessage(UnknownHostException("build.example"), deviceOnline = false),
        )
    }

    @Test
    fun `unknown connectivity keeps the previous wording`() {
        assertEquals(
            "You're offline or DNS failed. Check your internet connection and try again.",
            networkErrorMessage(UnknownHostException("build.example"), deviceOnline = null),
        )
    }

    @Test
    fun `timeout while online is not reported as an unreachable server`() {
        assertEquals(
            "Build server timed out. Check your internet connection and try again.",
            networkErrorMessage(SocketTimeoutException("timeout"), deviceOnline = true),
        )
    }

    @Test
    fun `unrecognised failure keeps its own message`() {
        assertEquals("TLS handshake aborted", networkErrorMessage(IOException("TLS handshake aborted"), true))
    }

    @Test
    fun `server unreachable message survives the build error mapping`() {
        val error = RemoteException(0, "NETWORK", SERVER_UNREACHABLE_MESSAGE)

        assertEquals(SERVER_UNREACHABLE_MESSAGE, BuildErrorMessages.userFacingBuildError(error))
    }
}
