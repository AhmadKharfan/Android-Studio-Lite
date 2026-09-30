package com.ahmadkharfan.androidstudiolite.data.githubactions.auth

import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.GitHubTokenGrant
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GitHubBuildCredentialsTest {

    private lateinit var server: MockWebServer
    private val vault = MemoryVault()
    private var now = 1_000_000L

    private val credentials by lazy {
        GitHubBuildCredentials(
            vault = vault,
            refresher = GitHubTokenRefresher("Iv23test", baseUrl = server.url("/")),
            nowMillis = { now },
        )
    }

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private val appGrant = GitHubTokenGrant(
        accessToken = "ghu_first",
        expiresInSeconds = 28_800,
        refreshToken = "ghr_first",
        refreshTokenExpiresInSeconds = 15_897_600,
    )

    private fun renewed(access: String, refresh: String) = MockResponse().setBody(
        """{"access_token":"$access","expires_in":28800,"refresh_token":"$refresh","refresh_token_expires_in":15897600}""",
    )

    @Test
    fun `a fresh token is used without refreshing`() = runBlocking {
        credentials.saveGrant(appGrant)

        assertEquals("ghu_first", credentials.accessToken())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `a token about to expire is refreshed with the client id and refresh token only`() = runBlocking {
        credentials.saveGrant(appGrant)
        now += 28_800_000 - 60_000
        server.enqueue(renewed("ghu_second", "ghr_second"))

        assertEquals("ghu_second", credentials.accessToken())

        val body = server.takeRequest().body.readUtf8()
        assertEquals("client_id=Iv23test&grant_type=refresh_token&refresh_token=ghr_first", body)
        assertFalse(body.contains("client_secret"))
        assertEquals("ghu_second", credentials.accessToken())
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `the renewed refresh token is used next time`() = runBlocking {
        credentials.saveGrant(appGrant)
        now += 28_800_000
        server.enqueue(renewed("ghu_second", "ghr_second"))
        credentials.accessToken()
        now += 28_800_000
        server.enqueue(renewed("ghu_third", "ghr_third"))

        assertEquals("ghu_third", credentials.accessToken())
        server.takeRequest()
        assertTrue(server.takeRequest().body.readUtf8().endsWith("refresh_token=ghr_second"))
    }

    @Test
    fun `concurrent callers share one refresh`() = runBlocking {
        credentials.saveGrant(appGrant)
        now += 28_800_000
        server.enqueue(renewed("ghu_second", "ghr_second"))

        val tokens = List(5) { async { credentials.accessToken() } }.awaitAll()

        assertEquals(List(5) { "ghu_second" }, tokens)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `a rejected refresh token signs the user out`() = runBlocking {
        credentials.saveGrant(appGrant)
        now += 28_800_000
        server.enqueue(MockResponse().setBody("""{"error":"bad_refresh_token","error_description":"expired"}"""))

        assertNull(credentials.accessToken())
        assertFalse(credentials.hasCredentials("github.com"))
        assertTrue(credentials.wasSignedOutByGitHub)
    }

    @Test
    fun `an expired refresh token signs the user out without calling github`() = runBlocking {
        credentials.saveGrant(appGrant)
        now += 15_897_600_000

        assertNull(credentials.accessToken())
        assertEquals(0, server.requestCount)
        assertTrue(credentials.wasSignedOutByGitHub)
    }

    @Test
    fun `connecting again or disconnecting forgets that github ended the connection`() = runBlocking {
        credentials.saveGrant(appGrant)
        now += 15_897_600_000
        credentials.accessToken()

        credentials.saveGrant(appGrant)
        assertFalse(credentials.wasSignedOutByGitHub)

        now += 15_897_600_000
        credentials.accessToken()
        credentials.clear("github.com")
        assertFalse(credentials.wasSignedOutByGitHub)
    }

    @Test
    fun `a user who never connected was not signed out by github`() {
        assertFalse(credentials.wasSignedOutByGitHub)
    }

    @Test
    fun `a network problem keeps the tokens for the next attempt`() = runBlocking {
        credentials.saveGrant(appGrant)
        now += 28_800_000
        server.enqueue(MockResponse().setResponseCode(503))

        assertEquals("ghu_first", credentials.accessToken())
        assertTrue(credentials.hasCredentials("github.com"))
    }

    @Test
    fun `a personal access token is kept as is and never refreshed`() = runBlocking {
        credentials.save("github.com", GitCredentials("", "github_pat_x"))
        now += 365L * 24 * 3_600_000

        assertEquals("github_pat_x", credentials.accessToken())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `only github dot com is managed`() {
        credentials.saveGrant(appGrant)

        assertEquals(GitCredentials("x-access-token", "ghu_first"), credentials.credentialsForHost("github.com"))
        assertEquals("ghu_first", credentials.credentialsForUrl("https://github.com/octo/asl-build.git")?.token)
        assertNull(credentials.credentialsForHost("gitlab.com"))
        assertFalse(credentials.hasCredentials("gitlab.com"))
    }

    @Test
    fun `clearing signs out`() = runBlocking {
        credentials.saveGrant(appGrant)

        credentials.clear("github.com")

        assertNull(credentials.accessToken())
        assertTrue(vault.values.isEmpty())
    }

    private class MemoryVault : TokenVault {
        val values = mutableMapOf<String, String>()
        override fun get(key: String) = values[key]
        override fun putAll(values: Map<String, String?>) {
            values.forEach { (key, value) -> if (value == null) this.values.remove(key) else this.values[key] = value }
        }
        override fun clear() = values.clear()
    }
}
