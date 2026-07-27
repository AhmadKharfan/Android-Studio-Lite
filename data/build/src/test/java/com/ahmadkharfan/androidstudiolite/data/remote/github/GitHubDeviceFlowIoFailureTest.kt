package com.ahmadkharfan.androidstudiolite.data.remote.github

import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthState
import com.ahmadkharfan.androidstudiolite.domain.time.MonotonicClock
import java.io.IOException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GitHubDeviceFlowIoFailureTest {

    @Test
    fun `device code request failure emits the current immediate error sequence`() = runTest {
        val authenticator = authenticator { throw IOException("offline") }

        assertEquals(
            listOf(
                GitHubDeviceAuthState.RequestingCode,
                GitHubDeviceAuthState.Error("offline"),
            ),
            authenticator.authenticate().toList(),
        )
    }

    @Test
    fun `three consecutive polling failures emit the current terminal error`() = runTest {
        val authenticator = authenticator { path ->
            if (path == DEVICE_CODE_PATH) DEVICE_CODE_JSON else throw IOException("offline")
        }

        assertEquals(
            listOf(
                GitHubDeviceAuthState.RequestingCode,
                GitHubDeviceAuthState.AwaitingAuthorization("ABCD-EFGH", "https://github.com/login/device"),
                GitHubDeviceAuthState.Error("offline"),
            ),
            authenticator.authenticate().toList(),
        )
    }

    private fun TestScope.authenticator(respondTo: (String) -> String): GitHubDeviceFlowAuthenticator {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(respondTo(chain.request().url.encodedPath).toResponseBody(JSON_MEDIA_TYPE))
                    .build()
            }
            .build()
        return GitHubDeviceFlowAuthenticator(
            clientId = "client-id",
            credentialStore = NoOpCredentialStore,
            httpClient = client,
            clock = MonotonicClock { testScheduler.currentTime },
            ioDispatcher = StandardTestDispatcher(testScheduler),
        )
    }

    private object NoOpCredentialStore : GitCredentialStore {
        override fun credentialsForUrl(url: String): GitCredentials? = null
        override fun credentialsForHost(host: String): GitCredentials? = null
        override fun hasCredentials(host: String): Boolean = false
        override fun save(host: String, credentials: GitCredentials) = Unit
        override fun clear(host: String) = Unit
        override val changes = emptyFlow<Unit>()
    }

    private companion object {
        const val DEVICE_CODE_PATH = "/login/device/code"
        val JSON_MEDIA_TYPE = "application/json".toMediaType()
        const val DEVICE_CODE_JSON =
            """{"device_code":"device-code","user_code":"ABCD-EFGH","verification_uri":"https://github.com/login/device","expires_in":30,"interval":5}"""
    }
}
