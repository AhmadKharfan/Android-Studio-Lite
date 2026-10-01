package com.ahmadkharfan.androidstudiolite.data.githubactions.readiness

import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildStorage
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildStorageCreation
import java.util.concurrent.CopyOnWriteArrayList
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
 * Creating the build storage against GitHub's real response shapes. Cloud Build calls with the App token
 * ([BUILD_TOKEN]), the Git sign-in with its OAuth token ([SIGN_IN_TOKEN]); responses depend on which.
 */
class GitHubBuildStorageCreatorTest {

    private lateinit var server: MockWebServer
    private val requests = CopyOnWriteArrayList<Triple<String, String, String>>()
    private val memory = BuildStorageMemory.inMemory()

    private var buildUser: MockResponse = json("""{"login":"octo"}""")
    private var signInUser: MockResponse = json("""{"login":"octo"}""").setHeader("X-OAuth-Scopes", "repo, workflow")
    private var create: MockResponse = json(repo(private = true), 201)
    private var existing: MockResponse = json("""{"message":"Not Found"}""", 404)
    private var signedIn = true
    private var online = true

    @Before
    fun setUp() {
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val token = request.getHeader("Authorization").orEmpty().removePrefix("Bearer ")
                    val route = "${request.method} ${request.path.orEmpty().substringBefore('?')}"
                    requests += Triple(token, route, request.body.readUtf8())
                    return when {
                        route == "GET /user" && token == BUILD_TOKEN -> buildUser
                        route == "GET /user" && token == SIGN_IN_TOKEN -> signInUser
                        route == "POST /user/repos" && token == SIGN_IN_TOKEN -> create
                        route == "GET /repos/octo/asl-build" && token == SIGN_IN_TOKEN -> existing
                        else -> json("""{"message":"Unexpected $route"}""", 500)
                    }.clone()
                }
            }
            start()
        }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun creator() = GitHubBuildStorageCreator(
        buildApi = GitHubApiClient(token = { BUILD_TOKEN }, baseUrl = server.url("/"), waitBeforeRetry = {}),
        signInApi = GitHubApiClient(token = { SIGN_IN_TOKEN }, baseUrl = server.url("/"), waitBeforeRetry = {}),
        hasSignIn = { signedIn },
        repositoryName = "asl-build",
        memory = memory,
        isOnline = { online },
    )

    private fun writes() = requests.filter { it.second.startsWith("POST") || it.second.startsWith("PATCH") || it.second.startsWith("DELETE") }

    @Test
    fun `creates a private repository with the git sign-in and remembers its id`() = runTest {
        val outcome = creator().create()

        assertEquals(CloudBuildStorageCreation.Created(storage(private = true)), outcome)
        val (token, _, body) = writes().single()
        assertEquals(SIGN_IN_TOKEN, token)
        assertTrue(body, body.contains("\"name\":\"asl-build\""))
        assertTrue(body, body.contains("\"private\":true"))
        assertEquals(REPO_ID, memory.repositoryId("octo"))
    }

    @Test
    fun `an existing private repository is used as is and remembered`() = runTest {
        create = nameTaken()
        existing = json(repo(private = true))

        val outcome = creator().create()

        assertEquals(CloudBuildStorageCreation.AlreadyExists(storage(private = true)), outcome)
        assertEquals(1, writes().size)
        assertEquals(REPO_ID, memory.repositoryId("octo"))
    }

    @Test
    fun `an existing public repository is left untouched and not remembered`() = runTest {
        create = nameTaken()
        existing = json(repo(private = false))

        val outcome = creator().create()

        assertEquals(CloudBuildStorageCreation.ExistsButPublic(storage(private = false)), outcome)
        assertEquals(1, writes().size)
        assertNull(memory.repositoryId("octo"))
    }

    @Test
    fun `a repository github reports as public after creating is not accepted`() = runTest {
        create = json(repo(private = false), 201)

        assertTrue(creator().create() is CloudBuildStorageCreation.Failed)
        assertNull(memory.repositoryId("octo"))
    }

    @Test
    fun `a sign-in without the repo scope isn't asked to create anything`() = runTest {
        signInUser = json("""{"login":"octo"}""").setHeader("X-OAuth-Scopes", "read:user, public_repo")

        assertEquals(CloudBuildStorageCreation.NotAllowed, creator().create())
        assertTrue(writes().isEmpty())
    }

    @Test
    fun `github refusing the creation means the sign-in isn't allowed`() = runTest {
        create = json("""{"message":"Resource not accessible by integration"}""", 403)

        assertEquals(CloudBuildStorageCreation.NotAllowed, creator().create())
    }

    @Test
    fun `a sign-in for another account creates nothing`() = runTest {
        signInUser = json("""{"login":"someone-else"}""").setHeader("X-OAuth-Scopes", "repo")

        assertEquals(CloudBuildStorageCreation.AccountMismatch("someone-else", "octo"), creator().create())
        assertTrue(writes().isEmpty())
    }

    @Test
    fun `an expired git sign-in says so`() = runTest {
        signInUser = json("""{"message":"Bad credentials"}""", 401)

        assertEquals(CloudBuildStorageCreation.SignInExpired, creator().create())
        assertTrue(writes().isEmpty())
    }

    @Test
    fun `without a cloud build connection there's no account to create for`() = runTest {
        buildUser = json("""{"message":"Bad credentials"}""", 401)

        assertEquals(CloudBuildStorageCreation.BuildNotConnected, creator().create())
        assertTrue(writes().isEmpty())
    }

    @Test
    fun `without a git sign-in nothing is attempted`() = runTest {
        signedIn = false

        assertFalse(creator().isAvailable)
        assertEquals(CloudBuildStorageCreation.NoSignIn, creator().create())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `offline is reported without calling github`() = runTest {
        online = false

        assertEquals(CloudBuildStorageCreation.Offline, creator().create())
        assertEquals(0, server.requestCount)
    }

    @Test
    fun `github errors while creating are unavailable, not a failed creation`() = runTest {
        create = json("""{"message":"Server Error"}""", 502)

        assertEquals(CloudBuildStorageCreation.GitHubUnavailable, creator().create())
        assertNull(memory.repositoryId("octo"))
    }

    @Test
    fun `an unreachable github is unavailable`() = runTest {
        val creator = creator()
        server.shutdown()

        assertEquals(CloudBuildStorageCreation.GitHubUnavailable, creator.create())
    }

    @Test
    fun `the remembered id finds the created repository in later readiness checks`() = runTest {
        creator().create()

        // Readiness looks the storage up by the remembered id first, so a later rename keeps it.
        assertEquals(REPO_ID, memory.repositoryId("OCTO"))
    }

    private fun nameTaken() = json(
        """{"message":"Repository creation failed.","errors":[{"resource":"Repository","code":"custom","field":"name",
        "message":"name already exists on this account"}]}""",
        422,
    )

    private fun storage(private: Boolean) =
        CloudBuildStorage(REPO_ID, "octo/asl-build", "https://github.com/octo/asl-build", isPrivate = private)

    private companion object {
        const val BUILD_TOKEN = "ghu_build"
        const val SIGN_IN_TOKEN = "gho_signin"
        const val REPO_ID = 4242L

        fun repo(private: Boolean) =
            """{"id":$REPO_ID,"full_name":"octo/asl-build","private":$private,"html_url":"https://github.com/octo/asl-build"}"""

        fun json(body: String, code: Int = 200) =
            MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)
    }
}
