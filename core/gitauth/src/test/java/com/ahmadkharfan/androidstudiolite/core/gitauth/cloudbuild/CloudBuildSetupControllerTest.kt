package com.ahmadkharfan.androidstudiolite.core.gitauth.cloudbuild

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildReadiness
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildState
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildStorage
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubBuildAccess
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthState
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CloudBuildSetupControllerTest {

    /** Answers checks from [answers] in order, repeating the last one, like the real readiness. */
    private class ScriptedReadiness(vararg answers: CloudBuildState) : CloudBuildReadiness {
        val queue = ArrayDeque(answers.toList())
        var checks = 0
        override val state = MutableStateFlow<CloudBuildState?>(null)
        override suspend fun check(): CloudBuildState {
            checks++
            val answer = if (queue.size > 1) queue.removeFirst() else queue.first()
            state.value = answer
            return answer
        }

        fun becomes(answer: CloudBuildState) {
            queue.clear()
            queue += answer
        }
    }

    private class FakeCredentials : GitCredentialStore {
        val cleared = mutableListOf<String>()
        override fun credentialsForUrl(url: String): GitCredentials? = null
        override fun credentialsForHost(host: String): GitCredentials? = null
        override fun hasCredentials(host: String): Boolean = false
        override fun save(host: String, credentials: GitCredentials) = Unit
        override fun clear(host: String) {
            cleared += host
        }

        override val changes: Flow<Unit> = emptyFlow()
    }

    private class FakeAccess(override val credentials: FakeCredentials = FakeCredentials()) : GitHubBuildAccess {
        override val usesGitHubApp = true
        override val installUrl = "https://github.com/apps/android-studio-lite-builds/installations/new"
        override val authenticator = object : GitHubDeviceAuthenticator {
            override val isConfigured = true
            override fun authenticate(): Flow<GitHubDeviceAuthState> = emptyFlow()
        }
    }

    private val scopes = mutableListOf<CoroutineScope>()
    private val access = FakeAccess()

    @After
    fun tearDown() = scopes.forEach { it.cancel() }

    private fun TestScope.controller(readiness: CloudBuildReadiness): Pair<CloudBuildSetupController, () -> CloudBuildSetupUiState> {
        var latest = CloudBuildSetupUiState()
        val scope = CoroutineScope(StandardTestDispatcher(testScheduler)).also { scopes += it }
        return CloudBuildSetupController(scope, readiness, access) { latest = it } to { latest }
    }

    @Test
    fun `opened from run, the build starts once a re-check turns ready`() = runTest {
        val readiness = ScriptedReadiness(CloudBuildState.StoragePublic(storage()))
        val (controller, ui) = controller(readiness)
        var builds = 0

        controller.open(CloudBuildSetupOrigin.Run) { builds++ }
        advanceUntilIdle()
        assertTrue(ui().visible)
        assertEquals(CloudBuildStep.StoragePublic, ui().screen.step)

        controller.onCloudBuildOpenedGitHub()
        readiness.becomes(CloudBuildState.Ready("octo", storage()))
        controller.onResumed()
        advanceUntilIdle()

        assertEquals(1, builds)
        assertFalse(ui().visible)
    }

    @Test
    fun `opened from run while already ready closes without building again`() = runTest {
        val readiness = ScriptedReadiness(CloudBuildState.Ready("octo", storage()))
        val (controller, ui) = controller(readiness)
        var builds = 0

        controller.open(CloudBuildSetupOrigin.Run) { builds++ }
        advanceUntilIdle()

        assertEquals(0, builds)
        assertFalse(ui().visible)
    }

    @Test
    fun `coming back without having gone to github doesn't re-check`() = runTest {
        val readiness = ScriptedReadiness(CloudBuildState.NotConnected)
        val (controller, _) = controller(readiness)
        controller.open(CloudBuildSetupOrigin.Run) {}
        advanceUntilIdle()
        val checks = readiness.checks

        controller.onResumed()
        advanceUntilIdle()

        assertEquals(checks, readiness.checks)
    }

    @Test
    fun `coming back from github re-checks once`() = runTest {
        val readiness = ScriptedReadiness(CloudBuildState.AccessMissing(null))
        val (controller, ui) = controller(readiness)
        controller.open(CloudBuildSetupOrigin.Run) {}
        advanceUntilIdle()
        val checks = readiness.checks

        controller.onCloudBuildOpenedGitHub()
        assertTrue(ui().awaitingReturn)
        controller.onResumed()
        controller.onResumed()
        advanceUntilIdle()

        assertEquals(checks + 1, readiness.checks)
        assertFalse(ui().awaitingReturn)
    }

    @Test
    fun `check again asks github again`() = runTest {
        val readiness = ScriptedReadiness(CloudBuildState.GitHubUnavailable)
        val (controller, ui) = controller(readiness)
        controller.open(CloudBuildSetupOrigin.Settings)
        advanceUntilIdle()

        readiness.becomes(CloudBuildState.Offline)
        controller.onCloudBuildCheckAgain()
        advanceUntilIdle()

        assertEquals(CloudBuildStep.Offline, ui().screen.step)
    }

    @Test
    fun `from settings, turning ready shows ready instead of closing`() = runTest {
        val readiness = ScriptedReadiness(CloudBuildState.NotConnected)
        val (controller, ui) = controller(readiness)
        controller.open(CloudBuildSetupOrigin.Settings)
        advanceUntilIdle()

        readiness.becomes(CloudBuildState.Ready("octo", null))
        controller.onCloudBuildCheckAgain()
        advanceUntilIdle()

        assertTrue(ui().visible)
        assertEquals(CloudBuildStep.Ready, ui().screen.step)
        assertEquals("octo", ui().screen.account)
    }

    @Test
    fun `connect opens the github sign-in for github dot com`() = runTest {
        val (controller, ui) = controller(ScriptedReadiness(CloudBuildState.NotConnected))

        controller.onCloudBuildConnect()
        advanceUntilIdle()

        assertTrue(ui().authPrompt.visible)
        assertEquals("github.com", ui().authPrompt.host)
    }

    @Test
    fun `switching account forgets this phone's connection and signs in again`() = runTest {
        val (controller, ui) = controller(ScriptedReadiness(CloudBuildState.Ready("octo", null)))

        controller.onCloudBuildSwitchAccount()
        advanceUntilIdle()

        assertEquals(listOf("github.com"), access.credentials.cleared)
        assertTrue(ui().authPrompt.visible)
    }

    @Test
    fun `background re-checks keep the shown state current`() = runTest {
        val readiness = ScriptedReadiness(CloudBuildState.NotConnected)
        val (controller, ui) = controller(readiness)
        controller.refresh()
        advanceUntilIdle()
        assertEquals(CloudBuildStep.Connect, ui().screen.step)

        readiness.state.value = CloudBuildState.Checking
        advanceUntilIdle()
        assertTrue(ui().checking)
        assertEquals(CloudBuildStep.Connect, ui().screen.step)

        readiness.state.value = CloudBuildState.Revoked
        advanceUntilIdle()
        assertFalse(ui().checking)
        assertEquals(CloudBuildStep.Reconnect, ui().screen.step)
    }

    private fun storage() = CloudBuildStorage(
        7,
        "octo/asl-build",
        "https://github.com/octo/asl-build",
        isPrivate = true,
    )
}
