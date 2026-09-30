package com.ahmadkharfan.androidstudiolite.data.githubactions.build

import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient
import com.ahmadkharfan.androidstudiolite.data.githubactions.readiness.BuildStorageLocator
import com.ahmadkharfan.androidstudiolite.data.githubactions.readiness.BuildStorageMemory
import com.ahmadkharfan.androidstudiolite.data.githubactions.workflow.BuildWorkflow
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BuildRepositoryProvisionerTest {

    private lateinit var server: MockWebServer
    private val responses = mutableMapOf<String, MockResponse>()
    private val requested = mutableListOf<String>()
    private val memory = BuildStorageMemory.inMemory()

    @Before
    fun setUp() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val key = "${request.method} ${request.path.orEmpty().substringBefore('?')}"
                    synchronized(requested) { requested += key }
                    return responses[key]?.clone() ?: json("""{"message":"Not Found"}""", 404)
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

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    private fun respond(path: String, body: String) {
        responses["GET $path"] = json(body)
    }

    private fun provisioner(): BuildRepositoryProvisioner {
        val api = GitHubApiClient(token = { "ghu_test" }, baseUrl = server.url("/"), waitBeforeRetry = {})
        return BuildRepositoryProvisioner(
            api = api,
            repositoryName = "asl-build",
            gitBaseUrl = "https://github.com/",
            setup = RepositorySetup.Manual(installUrl = null),
            waitBeforeRetry = {},
            locator = BuildStorageLocator(api, "asl-build", memory),
        )
    }

    private fun currentWorkflowIn(repo: String) = respond(
        "/repos/octo/$repo/contents/${BuildWorkflow.PATH}",
        """{"path":"${BuildWorkflow.PATH}","sha":"wf","encoding":"base64",
        "content":"${Buffer().writeUtf8(BuildWorkflow.contents()).readByteString().base64()}"}""",
    )

    @Test
    fun `a renamed build repository is still used through its remembered id`() = runBlocking {
        memory.remember("octo", 7)
        respond(
            "/user/installations",
            """{"installations":[{"id":42,"account":{"login":"octo"},"repository_selection":"selected"}]}""",
        )
        respond(
            "/user/installations/42/repositories",
            """{"repositories":[{"id":7,"full_name":"octo/renamed-builds","private":true,"default_branch":"main"}]}""",
        )
        currentWorkflowIn("renamed-builds")

        val repository = provisioner().ensure()

        assertEquals("renamed-builds", repository.name)
        assertEquals("https://github.com/octo/renamed-builds.git", repository.cloneUrl)
        assertFalse(requested.any { it.startsWith("PUT ") || it == "POST /user/repos" })
    }

    @Test
    fun `the build repository's id is remembered once found by name`() = runBlocking {
        respond("/repos/octo/asl-build", """{"id":9,"full_name":"octo/asl-build","private":true,"default_branch":"main"}""")
        currentWorkflowIn("asl-build")

        provisioner().ensure()

        assertEquals(9L, memory.repositoryId("octo"))
    }

    @Test
    fun `without a remembered id a missing repository asks for setup and looks no further`() = runBlocking {
        val failure = runCatching { provisioner().ensure() }.exceptionOrNull()

        assertTrue(failure is BuildFailure)
        assertFalse(requested.any { it.startsWith("GET /user/installations") })
    }
}
