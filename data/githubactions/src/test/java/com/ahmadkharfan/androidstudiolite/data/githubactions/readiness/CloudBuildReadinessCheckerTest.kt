package com.ahmadkharfan.androidstudiolite.data.githubactions.readiness

import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient
import com.ahmadkharfan.androidstudiolite.data.githubactions.build.RepositorySetup
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildState
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildStorage
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Readiness against GitHub's real response shapes: each test scripts what GitHub answers for the
 * endpoints a check calls, and the checker reads them through the real [GitHubApiClient].
 */
class CloudBuildReadinessCheckerTest {

    private lateinit var server: MockWebServer
    private val responses = mutableMapOf<String, MockResponse>()
    private val memory = BuildStorageMemory.inMemory()
    private var token: String? = "ghu_test"
    private var online = true
    private var signedOutByGitHub = false
    private var setup: RepositorySetup = RepositorySetup.Manual(INSTALL_URL)

    @Before
    fun setUp() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty().substringBefore('?')
                    return responses["${request.method} $path"]?.clone()
                        ?: json("""{"message":"Not Found"}""", 404)
                }
            }
            start()
        }
        respond("/user", """{"login":"octo"}""")
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun checker() = CloudBuildReadinessChecker(
        api = GitHubApiClient(token = { token }, baseUrl = server.url("/"), waitBeforeRetry = {}),
        token = { token },
        repositoryName = "asl-build",
        setup = setup,
        memory = memory,
        signals = ReadinessSignals(isOnline = { online }, signedOutByGitHub = { signedOutByGitHub }, nowEpochSeconds = { NOW }),
    )

    private suspend fun check() = checker().check()

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun respond(path: String, body: String, code: Int = 200, method: String = "GET") {
        responses["$method $path"] = json(body, code)
    }

    private fun installation(selection: String = "selected", suspendedAt: String? = null, login: String = "octo") =
        respond(
            "/user/installations",
            """{"total_count":1,"installations":[{"id":42,"account":{"login":"$login"},
            "repository_selection":"$selection","html_url":"$MANAGE_URL","app_slug":"android-studio-lite-builds",
            "suspended_at":${suspendedAt?.let { "\"$it\"" } ?: "null"}}]}""",
        )

    private fun repoJson(name: String = "asl-build", id: Long = 7, private: Boolean = true) =
        """{"id":$id,"full_name":"octo/$name","private":$private,"html_url":"https://github.com/octo/$name",
        "default_branch":"main"}"""

    private fun installationRepositories(vararg repos: String) =
        respond("/user/installations/42/repositories", """{"total_count":${repos.size},"repositories":[${repos.joinToString()}]}""")

    @Test
    fun `no token is not connected, without asking github`() = runTest {
        token = null

        assertEquals(CloudBuildState.NotConnected, check())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `no token after github ended the connection is revoked`() = runTest {
        token = null
        signedOutByGitHub = true

        assertEquals(CloudBuildState.Revoked, check())
    }

    @Test
    fun `a rejected token is revoked`() = runTest {
        respond("/user", """{"message":"Bad credentials"}""", 401)

        assertEquals(CloudBuildState.Revoked, check())
    }

    @Test
    fun `offline is reported without asking github`() = runTest {
        online = false

        assertEquals(CloudBuildState.Offline, check())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `no installation of the app is missing access, with the install link`() = runTest {
        respond("/user/installations", """{"total_count":0,"installations":[]}""")

        assertEquals(CloudBuildState.AccessMissing(INSTALL_URL), check())
    }

    @Test
    fun `an installation on another account doesn't count`() = runTest {
        installation(login = "some-org")

        assertEquals(CloudBuildState.AccessMissing(INSTALL_URL), check())
    }

    @Test
    fun `a suspended installation is paused, managed from github's own link`() = runTest {
        installation(suspendedAt = "2026-09-30T07:00:00Z")

        assertEquals(CloudBuildState.AccessPaused(MANAGE_URL), check())
    }

    @Test
    fun `selected repositories without the build repository is not reachable, not deleted`() = runTest {
        installation()
        installationRepositories(repoJson(name = "something-else", id = 99))

        assertEquals(CloudBuildState.StorageNotReachable(MANAGE_URL, wasReachableBefore = false), check())
    }

    @Test
    fun `a build repository reached before and now out of reach says so`() = runTest {
        memory.remember("octo", 7)
        installation()
        installationRepositories()

        assertEquals(CloudBuildState.StorageNotReachable(MANAGE_URL, wasReachableBefore = true), check())
    }

    @Test
    fun `selected repositories with the private build repository is ready and remembers its id`() = runTest {
        installation()
        installationRepositories(repoJson())

        assertEquals(
            CloudBuildState.Ready("octo", CloudBuildStorage(7, "octo/asl-build", "https://github.com/octo/asl-build", true)),
            check(),
        )
        assertEquals(7L, memory.repositoryId("octo"))
    }

    @Test
    fun `a public build repository is refused`() = runTest {
        installation()
        installationRepositories(repoJson(private = false))

        val state = check() as CloudBuildState.StoragePublic
        assertFalse(state.storage.isPrivate)
        assertEquals("octo/asl-build", state.storage.fullName)
    }

    @Test
    fun `a renamed build repository is recognised by its remembered id`() = runTest {
        memory.remember("octo", 7)
        installation()
        installationRepositories(repoJson(name = "renamed-builds", id = 7))

        val state = check() as CloudBuildState.Ready
        assertEquals("octo/renamed-builds", state.storage?.fullName)
    }

    @Test
    fun `a recreated build repository replaces the remembered id`() = runTest {
        memory.remember("octo", 7)
        installation()
        installationRepositories(repoJson(id = 8))

        assertTrue(check() is CloudBuildState.Ready)
        assertEquals(8L, memory.repositoryId("octo"))
    }

    @Test
    fun `all repositories with the build repository is ready`() = runTest {
        installation(selection = "all")
        respond("/repos/octo/asl-build", repoJson())

        assertTrue(check() is CloudBuildState.Ready)
    }

    @Test
    fun `all repositories without the build repository means it doesn't exist`() = runTest {
        installation(selection = "all")

        assertEquals(CloudBuildState.StorageMissing(MANAGE_URL), check())
    }

    @Test
    fun `all repositories finds a renamed build repository by id`() = runTest {
        memory.remember("octo", 7)
        installation(selection = "all")
        installationRepositories(repoJson(name = "other", id = 3), repoJson(name = "renamed-builds", id = 7))

        assertEquals("octo/renamed-builds", (check() as CloudBuildState.Ready).storage?.fullName)
    }

    @Test
    fun `an exhausted rate limit reports its reset time`() = runTest {
        responses["GET /user"] = json("""{"message":"API rate limit exceeded"}""", 403)
            .setHeader("X-RateLimit-Remaining", "0")
            .setHeader("X-RateLimit-Reset", "1900000000")

        assertEquals(CloudBuildState.RateLimited(1_900_000_000), check())
    }

    @Test
    fun `a secondary rate limit reports when to retry`() = runTest {
        responses["GET /user"] = json("""{"message":"You have exceeded a secondary rate limit"}""", 403)
            .setHeader("Retry-After", "60")

        assertEquals(CloudBuildState.RateLimited(NOW + 60), check())
    }

    @Test
    fun `an unreachable github while online is unavailable`() = runTest {
        val checker = checker()
        server.shutdown()

        assertEquals(CloudBuildState.GitHubUnavailable, checker.check())
    }

    @Test
    fun `a connection lost mid-check is offline when the device went offline`() = runTest {
        val checker = checker()
        server.shutdown()
        var calls = 0
        online = true
        val flaky = CloudBuildReadinessChecker(
            api = GitHubApiClient(token = { token }, baseUrl = server.url("/"), waitBeforeRetry = {}),
            token = { token },
            repositoryName = "asl-build",
            setup = setup,
            memory = memory,
            // Online when the check starts, offline by the time the request fails.
            signals = ReadinessSignals(isOnline = { calls++ == 0 }),
        )

        assertEquals(CloudBuildState.GitHubUnavailable, checker.check())
        assertEquals(CloudBuildState.Offline, flaky.check())
    }

    @Test
    fun `server errors are unavailable`() = runTest {
        respond("/user", """{"message":"Server Error"}""", 502)

        assertEquals(CloudBuildState.GitHubUnavailable, check())
    }

    @Test
    fun `a forbidden call naming the permissions it needs asks for updated access`() = runTest {
        installation()
        responses["GET /user/installations/42/repositories"] =
            json("""{"message":"Resource not accessible by integration"}""", 403)
                .setHeader("X-Accepted-GitHub-Permissions", "metadata=read")

        assertEquals(CloudBuildState.PermissionUpdateRequired(MANAGE_URL, "metadata=read"), check())
    }

    @Test
    fun `a forbidden call without a permission hint stays unknown`() = runTest {
        respond("/user/installations", """{"message":"Forbidden for some other reason"}""", 403)

        assertEquals(CloudBuildState.Unknown("Forbidden for some other reason"), check())
    }

    @Test
    fun `an unrecognised github answer stays unknown`() = runTest {
        respond("/user/installations", """{"message":"I'm a teapot"}""", 418)

        assertEquals(CloudBuildState.Unknown("I'm a teapot"), check())
    }

    @Test
    fun `with the git sign-in, a token with repo and workflow is ready without an installation check`() = runTest {
        setup = RepositorySetup.CreateIfMissing
        responses["GET /user"] = json("""{"login":"octo"}""").setHeader("X-OAuth-Scopes", "repo, workflow")

        assertEquals(CloudBuildState.Ready("octo", null), check())
        assertNull(server.takeRequestPaths().firstOrNull { it.startsWith("/user/installations") })
    }

    @Test
    fun `with the git sign-in, a token without workflow needs a new sign-in`() = runTest {
        setup = RepositorySetup.CreateIfMissing
        responses["GET /user"] = json("""{"login":"octo"}""").setHeader("X-OAuth-Scopes", "repo")

        assertEquals(CloudBuildState.PermissionUpdateRequired(null, null), check())
    }

    private fun MockWebServer.takeRequestPaths(): List<String> =
        List(requestCount) { takeRequest().path.orEmpty() }

    private companion object {
        const val NOW = 1_800_000_000L
        const val INSTALL_URL = "https://github.com/apps/android-studio-lite-builds/installations/new"
        const val MANAGE_URL = "https://github.com/settings/installations/42"
    }
}
