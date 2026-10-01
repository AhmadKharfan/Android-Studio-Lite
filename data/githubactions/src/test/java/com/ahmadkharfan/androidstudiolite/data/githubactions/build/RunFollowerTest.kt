package com.ahmadkharfan.androidstudiolite.data.githubactions.build

import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiException
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RunFollowerTest {

    private lateinit var server: MockWebServer
    private val waits = mutableListOf<Long>()
    private var now = 0L
    private val handle = BuildHandle("octo", "asl-build", "corr-0001", 77)

    @Before
    fun setUp() {
        server = MockWebServer().apply { start() }
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun follower() = RunFollower(
        api = GitHubApiClient(token = { "t" }, baseUrl = server.url("/"), waitBeforeRetry = {}),
        clock = { now },
        wait = { waits += it; now += it },
        nowEpochSeconds = { 1_000L },
    )

    private fun run(status: String, conclusion: String? = null) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setBody("""{"id":77,"status":"$status","conclusion":${conclusion?.let { "\"$it\"" } ?: "null"}}""")

    private fun unavailable() = MockResponse().setResponseCode(503).setBody("""{"message":"Unavailable"}""")

    @Test
    fun `transient outages are ridden out and reported once`() = runBlocking {
        repeat(6) { server.enqueue(unavailable()) } // two polls, each retried three times by the client
        server.enqueue(run("completed", "success"))
        val events = mutableListOf<BuildEvent>()

        val finished = follower().follow(handle, 77, deadlineMillis = Long.MAX_VALUE, liveLog = null) { events += it }

        assertEquals("success", finished.conclusion)
        assertEquals(1, events.count { it is BuildEvent.Problem && it.message.startsWith("Connection to GitHub lost") })
    }

    @Test
    fun `exhausted rate limit waits until it resets`() = runBlocking {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody("""{"message":"API rate limit exceeded"}""")
                .setHeader("X-RateLimit-Limit", "5000")
                .setHeader("X-RateLimit-Remaining", "0")
                .setHeader("X-RateLimit-Reset", "1030"),
        )
        server.enqueue(run("completed", "success"))

        follower().follow(handle, 77, deadlineMillis = Long.MAX_VALUE, liveLog = null) {}

        assertEquals(listOf(30_000L), waits)
    }

    @Test
    fun `permission errors stop following immediately`() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"message":"Resource not accessible"}"""))

        val error = runCatching { follower().follow(handle, 77, deadlineMillis = Long.MAX_VALUE, liveLog = null) {} }.exceptionOrNull()

        assertTrue(error is GitHubApiException)
        assertEquals(1, server.requestCount)
    }

    /** Routes by path so polls and job lookups can interleave freely. */
    private fun scripted(status: () -> String, jobs: () -> String) {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                return when {
                    request.method == "POST" && path.endsWith("/cancel") -> MockResponse().setResponseCode(409)
                    path.endsWith("/jobs") -> MockResponse().setHeader("Content-Type", "application/json").setBody(jobs())
                    else -> run(status())
                }
            }
        }
    }

    @Test
    fun `a run github keeps queued without a job is given up on with a clear message`() = runBlocking {
        scripted(status = { "queued" }, jobs = { """{"total_count":0,"jobs":[]}""" })

        val error = runCatching { follower().follow(handle, 77, deadlineMillis = Long.MAX_VALUE, liveLog = null) {} }.exceptionOrNull()

        assertTrue(error is BuildFailure)
        assertTrue(error!!.message!!, error.message!!.startsWith("GitHub accepted the build but hasn't started it."))
        assertTrue(now in 180_000L..200_000L)
    }

    @Test
    fun `a queued run whose job exists is waiting for a machine and keeps being followed`() = runBlocking {
        var polls = 0
        scripted(
            status = { if (++polls > 60) "completed" else "queued" },
            jobs = { """{"jobs":[{"id":5,"name":"Build","status":"queued"}]}""" },
        )

        val finished = follower().follow(handle, 77, deadlineMillis = Long.MAX_VALUE, liveLog = null) {}

        assertTrue(finished.isCompleted)
        assertTrue(now > 180_000L)
    }

    @Test
    fun `a run waiting on another of the same group is pending and keeps being followed`() = runBlocking {
        var polls = 0
        scripted(status = { if (++polls > 60) "completed" else "pending" }, jobs = { """{"jobs":[]}""" })

        val finished = follower().follow(handle, 77, deadlineMillis = Long.MAX_VALUE, liveLog = null) {}

        assertTrue(finished.isCompleted)
    }

    @Test
    fun `a run past its deadline fails the build`() = runBlocking {
        repeat(5) { server.enqueue(run("in_progress")) }
        repeat(5) { server.enqueue(MockResponse().setBody("""{"jobs":[]}""")) }

        val error = runCatching { follower().follow(handle, 77, deadlineMillis = 7_000, liveLog = null) {} }.exceptionOrNull()

        assertTrue(error is BuildFailure)
    }
}
