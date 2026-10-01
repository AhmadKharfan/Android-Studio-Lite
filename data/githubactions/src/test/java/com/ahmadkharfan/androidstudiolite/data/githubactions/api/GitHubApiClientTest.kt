package com.ahmadkharfan.androidstudiolite.data.githubactions.api

import java.io.File
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitHubApiClientTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var server: MockWebServer
    private val waits = mutableListOf<Long>()
    private var token: String? = "gho_test"

    private val client by lazy {
        GitHubApiClient(
            token = { token },
            baseUrl = server.url("/"),
            waitBeforeRetry = { waits += it },
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

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    @Test
    fun `requests carry the token, api version and json accept header`() = runTest {
        server.enqueue(json("""{"login":"octo"}"""))

        client.authenticatedUser()

        val request = server.takeRequest()
        assertEquals("/user", request.path)
        assertEquals("Bearer gho_test", request.getHeader("Authorization"))
        assertEquals("2022-11-28", request.getHeader("X-GitHub-Api-Version"))
        assertEquals("application/vnd.github+json", request.getHeader("Accept"))
    }

    @Test
    fun `authenticated user reports granted oauth scopes`() = runTest {
        server.enqueue(json("""{"login":"octo"}""").setHeader("X-OAuth-Scopes", "repo, workflow"))

        assertEquals(AuthenticatedUser("octo", setOf("repo", "workflow")), client.authenticatedUser())
    }

    @Test
    fun `tokens without scope reporting yield null scopes`() = runTest {
        server.enqueue(json("""{"login":"octo"}"""))

        assertNull(client.authenticatedUser().scopes)
    }

    @Test
    fun `missing token fails as unauthorized without calling github`() = runTest {
        token = null

        val error = runCatching { client.authenticatedUser() }.exceptionOrNull() as GitHubApiException

        assertTrue(error.isUnauthorized)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `missing repository is null, not an error`() = runTest {
        server.enqueue(json("""{"message":"Not Found"}""", 404))

        assertNull(client.repository("octo", "asl-build"))
    }

    @Test
    fun `created repository is private and initialised`() = runTest {
        server.enqueue(json("""{"full_name":"octo/asl-build","private":true,"default_branch":"main"}""", 201))

        val repo = client.createUserRepository("asl-build", "Builds for Android Studio Lite")

        assertEquals(GitHubRepository("octo/asl-build", true, "main"), repo)
        val body = server.takeRequest().body.readUtf8()
        assertTrue(body, body.contains("\"private\":true") && body.contains("\"auto_init\":true"))
    }

    @Test
    fun `file content is decoded from github's wrapped base64`() = runTest {
        // GitHub wraps base64 content at 60 characters.
        val wrapped = "bmFtZTogQVNMIEJ1aWxkCiMgYXNsLXdvcmtmbG93LXZlcnNpb246IDEK\nCg=="
        server.enqueue(json("""{"path":"a.yml","sha":"abc","content":"$wrapped","encoding":"base64"}"""))

        val file = client.file("octo", "asl-build", ".github/workflows/a.yml")

        assertEquals("abc", file?.sha)
        assertEquals("name: ASL Build\n# asl-workflow-version: 1\n\n", file?.content)
    }

    @Test
    fun `put file sends base64 content and the sha being replaced`() = runTest {
        server.enqueue(json("""{"content":{}}""", 200))

        client.putFile("octo", "asl-build", ".github/workflows/a.yml", "hi\n", "Update", sha = "abc")

        val request = server.takeRequest()
        assertEquals("PUT", request.method)
        assertEquals("/repos/octo/asl-build/contents/.github/workflows/a.yml", request.path)
        val body = request.body.readUtf8()
        assertTrue(body, body.contains("\"content\":\"aGkK\"") && body.contains("\"sha\":\"abc\""))
    }

    @Test
    fun `dispatch returns the run id when github reports it`() = runTest {
        server.enqueue(json("""{"workflow_run_id":42,"run_url":"u","html_url":"h"}"""))

        val runId = client.dispatchWorkflow("octo", "asl-build", "asl-build.yml", "main", mapOf("tasks" to "a"))

        assertEquals(42L, runId)
        val request = server.takeRequest()
        assertEquals("/repos/octo/asl-build/actions/workflows/asl-build.yml/dispatches", request.path)
        assertEquals("""{"ref":"main","inputs":{"tasks":"a"},"return_run_details":true}""", request.body.readUtf8())
    }

    @Test
    fun `dispatch without a run id in the response returns null`() = runTest {
        server.enqueue(MockResponse().setResponseCode(204))

        assertNull(client.dispatchWorkflow("octo", "asl-build", "asl-build.yml", "main", emptyMap()))
    }

    @Test
    fun `dispatch is never retried`() = runTest {
        server.enqueue(json("""{"message":"Server Error"}""", 502))

        val error = runCatching {
            client.dispatchWorkflow("octo", "asl-build", "asl-build.yml", "main", emptyMap())
        }.exceptionOrNull() as GitHubApiException

        assertEquals(502, error.httpStatus)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `reads are retried on transient failures`() = runTest {
        server.enqueue(json("""{"message":"Bad Gateway"}""", 502))
        server.enqueue(json("""{"message":"Server Error"}""", 500))
        server.enqueue(json("""{"id":7,"status":"queued"}"""))

        val run = client.run("octo", "asl-build", 7)

        assertEquals("queued", run.status)
        assertEquals(listOf(1_000L, 2_000L), waits)
    }

    @Test
    fun `unreachable github is a transient transport failure`() = runTest {
        server.shutdown()

        val error = runCatching { client.run("octo", "asl-build", 7) }.exceptionOrNull() as GitHubApiException

        assertEquals(0, error.httpStatus)
        assertTrue(error.isTransient)
        assertEquals(2, waits.size)
    }

    @Test
    fun `reads give up after three attempts`() = runTest {
        repeat(3) { server.enqueue(json("""{"message":"Unavailable"}""", 503)) }

        val error = runCatching { client.run("octo", "asl-build", 7) }.exceptionOrNull() as GitHubApiException

        assertEquals(503, error.httpStatus)
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `client errors are not retried and keep github's message`() = runTest {
        server.enqueue(json("""{"message":"Workflow does not have 'workflow_dispatch' trigger"}""", 422))

        val error = runCatching {
            client.dispatchedRuns("octo", "asl-build", "asl-build.yml", "main")
        }.exceptionOrNull() as GitHubApiException

        assertTrue(error.isValidationFailure)
        assertEquals("Workflow does not have 'workflow_dispatch' trigger", error.message)
        assertEquals(1, server.requestCount)
    }

    @Test
    fun `unchanged resources are served from the etag cache`() = runTest {
        server.enqueue(json("""{"id":7,"status":"in_progress"}""").setHeader("ETag", "\"v1\""))
        server.enqueue(MockResponse().setResponseCode(304))

        client.run("octo", "asl-build", 7)
        val second = client.run("octo", "asl-build", 7)

        assertEquals("in_progress", second.status)
        server.takeRequest()
        assertEquals("\"v1\"", server.takeRequest().getHeader("If-None-Match"))
    }

    @Test
    fun `exhausted primary rate limit is reported with its reset time`() = runTest {
        server.enqueue(
            json("""{"message":"API rate limit exceeded"}""", 403)
                .setHeader("X-RateLimit-Limit", "5000")
                .setHeader("X-RateLimit-Remaining", "0")
                .setHeader("X-RateLimit-Reset", "1760000000"),
        )

        val error = runCatching { client.run("octo", "asl-build", 7) }.exceptionOrNull() as GitHubApiException

        assertTrue(error.isRateLimited)
        assertEquals(1_760_000_000L, error.rateLimitResetEpochSeconds)
        assertEquals(RateLimit(5000, 0, 1_760_000_000L), client.lastRateLimit)
    }

    @Test
    fun `secondary rate limit reports retry-after`() = runTest {
        server.enqueue(json("""{"message":"secondary rate limit"}""", 403).setHeader("Retry-After", "60"))

        val error = runCatching { client.run("octo", "asl-build", 7) }.exceptionOrNull() as GitHubApiException

        assertEquals(60L, error.retryAfterSeconds)
    }

    @Test
    fun `plain forbidden is not mistaken for a rate limit`() = runTest {
        server.enqueue(json("""{"message":"Resource not accessible by integration"}""", 403))

        val error = runCatching { client.run("octo", "asl-build", 7) }.exceptionOrNull() as GitHubApiException

        assertFalse(error.isRateLimited)
        assertFalse(error.isMissingPermission)
    }

    @Test
    fun `forbidden naming the accepted permissions is a missing permission`() = runTest {
        server.enqueue(
            json("""{"message":"Resource not accessible by integration"}""", 403)
                .setHeader("X-Accepted-GitHub-Permissions", "actions=write"),
        )

        val error = runCatching { client.run("octo", "asl-build", 7) }.exceptionOrNull() as GitHubApiException

        assertTrue(error.isMissingPermission)
        assertEquals("actions=write", error.acceptedPermissions)
    }

    @Test
    fun `app installations are read with their access details`() = runTest {
        server.enqueue(
            json(
                """{"total_count":1,"installations":[{"id":42,"account":{"login":"octo"},"repository_selection":"selected",
                "html_url":"https://github.com/settings/installations/42","suspended_at":null,"app_slug":"asl"}]}""",
            ),
        )

        val installation = client.userInstallations().single()

        assertEquals(
            AppInstallation(42, InstallationAccount("octo"), "selected", "https://github.com/settings/installations/42", null),
            installation,
        )
        assertFalse(installation.coversAllRepositories)
        assertEquals("/user/installations?per_page=100&page=1", server.takeRequest().path)
    }

    @Test
    fun `installation repositories are read across full pages`() = runTest {
        val fullPage = (1..100).joinToString { """{"id":$it,"full_name":"octo/r$it","private":true}""" }
        server.enqueue(json("""{"total_count":101,"repositories":[$fullPage]}"""))
        server.enqueue(json("""{"total_count":101,"repositories":[{"id":101,"full_name":"octo/asl-build","private":true,"html_url":"https://github.com/octo/asl-build"}]}"""))

        val repositories = client.installationRepositories(42)

        assertEquals(101, repositories.size)
        assertEquals(GitHubRepository("octo/asl-build", true, id = 101, htmlUrl = "https://github.com/octo/asl-build"), repositories.last())
        assertEquals("/user/installations/42/repositories?per_page=100&page=1", server.takeRequest().path)
        assertEquals("/user/installations/42/repositories?per_page=100&page=2", server.takeRequest().path)
    }

    @Test
    fun `artifact download follows the redirect without leaking the token`() = runTest {
        val storage = MockWebServer().apply { start() }
        try {
            storage.enqueue(MockResponse().setBody("zip-bytes"))
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", storage.url("/blob/1.zip")))
            val destination = File(tmp.root, "out/result.zip")

            client.downloadArtifact("octo", "asl-build", 9, destination)

            assertEquals("zip-bytes", destination.readText())
            assertFalse(File(tmp.root, "out/result.zip.part").exists())
            assertEquals("/repos/octo/asl-build/actions/artifacts/9/zip", server.takeRequest().path)
            assertNull(storage.takeRequest().getHeader("Authorization"))
        } finally {
            storage.shutdown()
        }
    }

    @Test
    fun `failed download leaves no partial file`() = runTest {
        server.enqueue(json("""{"message":"Gone"}""", 410))
        val destination = File(tmp.root, "result.zip")

        val error = runCatching { client.downloadArtifact("octo", "asl-build", 9, destination) }.exceptionOrNull()

        assertEquals(410, (error as GitHubApiException).httpStatus)
        assertFalse(destination.exists())
        assertFalse(File(tmp.root, "result.zip.part").exists())
    }

    @Test
    fun `runs jobs and artifacts are parsed`() = runTest {
        server.enqueue(json("""{"workflow_runs":[{"id":3,"display_title":"asl-abc","status":"queued"}]}"""))
        server.enqueue(
            json("""{"jobs":[{"id":5,"name":"Build","status":"in_progress","steps":[{"name":"Build","number":7,"status":"in_progress"}]}]}"""),
        )
        server.enqueue(json("""{"artifacts":[{"id":9,"name":"asl-result","size_in_bytes":1024,"expired":false}]}"""))

        val runs = client.dispatchedRuns("octo", "asl-build", "asl-build.yml", "main")
        val jobs = client.jobs("octo", "asl-build", 3)
        val artifacts = client.artifacts("octo", "asl-build", 3)

        assertEquals("asl-abc", runs.single().displayTitle)
        assertEquals("Build", jobs.single().steps.single().name)
        assertEquals(WorkflowArtifact(9, "asl-result", 1024, false), artifacts.single())
        val runsRequest = server.takeRequest()
        assertEquals(
            "/repos/octo/asl-build/actions/workflows/asl-build.yml/runs?event=workflow_dispatch&branch=main&per_page=20",
            runsRequest.path,
        )
    }

    @Test
    fun `cancel uses force-cancel when asked`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202))
        server.enqueue(MockResponse().setResponseCode(202))

        client.cancelRun("octo", "asl-build", 3)
        client.cancelRun("octo", "asl-build", 3, force = true)

        assertEquals("/repos/octo/asl-build/actions/runs/3/cancel", server.takeRequest().path)
        assertEquals("/repos/octo/asl-build/actions/runs/3/force-cancel", server.takeRequest().path)
    }
}
