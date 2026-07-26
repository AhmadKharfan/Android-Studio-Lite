package com.ahmadkharfan.androidstudiolite.data.remote.github

import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthState
import com.ahmadkharfan.androidstudiolite.domain.time.MonotonicClock
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubDeviceFlowAuthenticatorTest {

    @Test
    fun `granted authorization emits success and saves GitHub credentials`() = runTest {
        val credentialStore = RecordingCredentialStore()
        val authenticator = authenticator(credentialStore) { path ->
            when (path) {
                DEVICE_CODE_PATH -> deviceCodeJson(expiresIn = 30)
                ACCESS_TOKEN_PATH -> """{"access_token":"secret-token"}"""
                USER_PATH -> """{"login":"octocat"}"""
                else -> "{}"
            }
        }

        val states = authenticator.authenticate().toList()

        assertEquals(GitHubDeviceAuthState.Success("octocat"), states.last())
        assertEquals(GitHubDeviceFlowAuthenticator.GITHUB_HOST, credentialStore.savedHost)
        assertEquals("secret-token", credentialStore.savedCredentials?.token)
    }

    @Test
    fun `denied authorization emits error without saving credentials`() = runTest {
        val credentialStore = RecordingCredentialStore()
        val authenticator = authenticator(credentialStore) { path ->
            when (path) {
                DEVICE_CODE_PATH -> deviceCodeJson(expiresIn = 30)
                ACCESS_TOKEN_PATH -> """{"error":"access_denied"}"""
                else -> "{}"
            }
        }

        val states = authenticator.authenticate().toList()

        assertTrue(states.last() is GitHubDeviceAuthState.Error)
        assertNull(credentialStore.savedHost)
        assertNull(credentialStore.savedCredentials)
    }

    @Test
    fun `slow down increases the delay before the next poll`() = runTest {
        val credentialStore = RecordingCredentialStore()
        val pollTimes = mutableListOf<Long>()
        var tokenPolls = 0
        val authenticator = authenticator(
            credentialStore = credentialStore,
            onRequest = { path ->
                if (path == ACCESS_TOKEN_PATH) pollTimes += testScheduler.currentTime
            },
        ) { path ->
            when (path) {
                DEVICE_CODE_PATH -> deviceCodeJson(expiresIn = 15)
                ACCESS_TOKEN_PATH -> if (tokenPolls++ == 0) {
                    """{"error":"slow_down"}"""
                } else {
                    """{"error":"authorization_pending"}"""
                }
                else -> "{}"
            }
        }

        authenticator.authenticate().toList()

        assertEquals(listOf(5_000L, 15_000L), pollTimes)
        assertTrue(pollTimes[1] - pollTimes[0] > 5_000L)
    }

    @Test
    fun `pending authorization expires and stops polling`() = runTest {
        val credentialStore = RecordingCredentialStore()
        var tokenPolls = 0
        val authenticator = authenticator(credentialStore) { path ->
            when (path) {
                DEVICE_CODE_PATH -> deviceCodeJson(expiresIn = 12)
                ACCESS_TOKEN_PATH -> {
                    tokenPolls++
                    """{"error":"authorization_pending"}"""
                }
                else -> "{}"
            }
        }

        val states = authenticator.authenticate().toList()
        val completionTime = testScheduler.currentTime
        val pollsAtExpiry = tokenPolls
        advanceTimeBy(60_000)

        val error = states.last() as GitHubDeviceAuthState.Error
        assertTrue(error.message.contains("expired", ignoreCase = true))
        assertTrue(completionTime >= 12_000L)
        assertEquals(pollsAtExpiry, tokenPolls)
    }

    @Test
    fun `blank client id is unconfigured and emits an immediate error`() = runTest {
        val credentialStore = RecordingCredentialStore()
        var requestCount = 0
        val authenticator = authenticator(
            credentialStore = credentialStore,
            clientId = " ",
            onRequest = { requestCount++ },
        ) { "{}" }

        val states = authenticator.authenticate().toList()

        assertFalse(authenticator.isConfigured)
        assertEquals(1, states.size)
        assertTrue(states.single() is GitHubDeviceAuthState.Error)
        assertEquals(0L, testScheduler.currentTime)
        assertEquals(0, requestCount)
    }

    private fun TestScope.authenticator(
        credentialStore: RecordingCredentialStore,
        clientId: String = "client-id",
        onRequest: (String) -> Unit = {},
        respondTo: (String) -> String,
    ): GitHubDeviceFlowAuthenticator {
        val httpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val path = chain.request().url.encodedPath
                onRequest(path)
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(respondTo(path).toResponseBody(JSON_MEDIA_TYPE))
                    .build()
            }
            .build()
        return GitHubDeviceFlowAuthenticator(
            clientId = clientId,
            credentialStore = credentialStore,
            httpClient = httpClient,
            clock = MonotonicClock { testScheduler.currentTime },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
    }

    private class RecordingCredentialStore : GitCredentialStore {
        var savedHost: String? = null
        var savedCredentials: GitCredentials? = null

        override fun credentialsForUrl(url: String): GitCredentials? = null

        override fun credentialsForHost(host: String): GitCredentials? = null

        override fun hasCredentials(host: String): Boolean = false

        override fun save(host: String, credentials: GitCredentials) {
            savedHost = host
            savedCredentials = credentials
        }

        override fun clear(host: String) = Unit

        override val changes: Flow<Unit> = emptyFlow()
    }

    private companion object {
        const val DEVICE_CODE_PATH = "/login/device/code"
        const val ACCESS_TOKEN_PATH = "/login/oauth/access_token"
        const val USER_PATH = "/user"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()

        fun deviceCodeJson(expiresIn: Int): String =
            """
            {
              "device_code": "device-code",
              "user_code": "ABCD-EFGH",
              "verification_uri": "https://github.com/login/device",
              "expires_in": $expiresIn,
              "interval": 5
            }
            """.trimIndent()
    }
}
