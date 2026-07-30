package com.ahmadkharfan.androidstudiolite.core.gitauth

import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthState
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthenticator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterization tests for [GitAuthController].
 *
 * These pin the *current* behaviour of the shared git-auth state machine, which is consumed both by
 * `:feature:git` itself and by `:feature:settings`. A later phase reshapes that cross-feature
 * boundary; these tests exist so that refactor is provably behaviour-preserving. They deliberately
 * assert what the code does today, including the quirks noted in individual test names.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GitAuthControllerCharacterizationTest {

    /**
     * The controller launches into a caller-supplied scope. Tests hand it a scope backed by the
     * `runTest` scheduler so `advanceUntilIdle()` drives it (`backgroundScope` is deliberately
     * ignored by the scheduler's idleness check), and cancel it afterwards so a suspended collector
     * cannot outlive its test.
     */
    private val controllerScopes = mutableListOf<CoroutineScope>()

    @After
    fun cancelControllerScopes() {
        controllerScopes.forEach { it.cancel() }
        controllerScopes.clear()
    }

    private fun TestScope.controllerScope(): CoroutineScope =
        CoroutineScope(StandardTestDispatcher(testScheduler)).also { controllerScopes += it }

    private class FakeCredentialStore(
        private val hasCredentialsFor: Set<String> = emptySet(),
    ) : GitCredentialStore {
        val saved = mutableListOf<Pair<String, GitCredentials>>()
        val cleared = mutableListOf<String>()

        override fun credentialsForUrl(url: String): GitCredentials? = null
        override fun credentialsForHost(host: String): GitCredentials? = null
        override fun hasCredentials(host: String): Boolean = host in hasCredentialsFor
        override fun save(host: String, credentials: GitCredentials) {
            saved += host to credentials
        }

        override fun clear(host: String) {
            cleared += host
        }

        override val changes: Flow<Unit> = emptyFlow()
    }

    private class FakeAuthenticator(
        override val isConfigured: Boolean = true,
        private val steps: Flow<GitHubDeviceAuthState> = emptyFlow(),
    ) : GitHubDeviceAuthenticator {
        var authenticateCallCount = 0
        override fun authenticate(): Flow<GitHubDeviceAuthState> {
            authenticateCallCount++
            return steps
        }
    }

    private class Recorder {
        val states = mutableListOf<GitAuthPromptState>()
        val latest: GitAuthPromptState get() = states.last()
        fun emit(state: GitAuthPromptState) {
            states += state
        }
    }

    // ---- open() ---------------------------------------------------------------------------

    @Test
    fun `open shows prompt in SignIn mode when github is configured`() = runTest {
        val recorder = Recorder()
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = FakeCredentialStore(),
            authenticator = FakeAuthenticator(isConfigured = true),
            emit = recorder::emit,
        )

        controller.open(host = "github.com") {}

        assertTrue(recorder.latest.visible)
        assertEquals("github.com", recorder.latest.host)
        assertTrue(recorder.latest.gitHubAvailable)
        assertEquals(GitAuthMode.SignIn, recorder.latest.mode)
    }

    @Test
    fun `open falls back to Token mode when github is not configured`() = runTest {
        val recorder = Recorder()
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = FakeCredentialStore(),
            authenticator = FakeAuthenticator(isConfigured = false),
            emit = recorder::emit,
        )

        controller.open(host = "example.com") {}

        assertEquals(GitAuthMode.Token, recorder.latest.mode)
        assertFalse(recorder.latest.gitHubAvailable)
    }

    @Test
    fun `hasCredentials is false for a null host and otherwise delegates to the store`() = runTest {
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = FakeCredentialStore(hasCredentialsFor = setOf("github.com")),
            authenticator = FakeAuthenticator(),
            emit = {},
        )

        assertFalse(controller.hasCredentials(null))
        assertTrue(controller.hasCredentials("github.com"))
        assertFalse(controller.hasCredentials("gitlab.com"))
    }

    // ---- token submission -----------------------------------------------------------------

    @Test
    fun `submitting a blank token reports an error and does not save or retry`() = runTest {
        val store = FakeCredentialStore()
        val recorder = Recorder()
        var retried = false
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = store,
            authenticator = FakeAuthenticator(),
            emit = recorder::emit,
        )
        controller.open(host = "github.com") { retried = true }

        controller.onAuthTokenChanged("   ")
        controller.onSubmitAuthToken()

        assertEquals("Enter an access token", recorder.latest.error)
        assertTrue(store.saved.isEmpty())
        assertFalse(retried)
    }

    @Test
    fun `submitting a token saves it trimmed with an empty username then retries and resets`() = runTest {
        val store = FakeCredentialStore()
        val recorder = Recorder()
        var retried = false
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = store,
            authenticator = FakeAuthenticator(),
            emit = recorder::emit,
        )
        controller.open(host = "github.com") { retried = true }

        controller.onAuthTokenChanged("  ghp_secret  ")
        controller.onSubmitAuthToken()

        assertEquals(1, store.saved.size)
        assertEquals("github.com", store.saved.single().first)
        assertEquals("ghp_secret", store.saved.single().second.token)
        assertEquals("", store.saved.single().second.username)
        assertTrue(retried)
        assertFalse(recorder.latest.visible)
        assertEquals("", recorder.latest.token)
    }

    @Test
    fun `submitting a token with a null host still retries but saves nothing`() = runTest {
        val store = FakeCredentialStore()
        var retried = false
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = store,
            authenticator = FakeAuthenticator(),
            emit = {},
        )
        controller.open(host = null) { retried = true }

        controller.onAuthTokenChanged("ghp_secret")
        controller.onSubmitAuthToken()

        assertTrue(store.saved.isEmpty())
        assertTrue(retried)
    }

    @Test
    fun `changing the token clears a previous error`() = runTest {
        val recorder = Recorder()
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = FakeCredentialStore(),
            authenticator = FakeAuthenticator(),
            emit = recorder::emit,
        )
        controller.open(host = "github.com") {}
        controller.onSubmitAuthToken()
        assertEquals("Enter an access token", recorder.latest.error)

        controller.onAuthTokenChanged("x")

        assertNull(recorder.latest.error)
        assertEquals("x", recorder.latest.token)
    }

    // ---- github device flow ---------------------------------------------------------------

    @Test
    fun `starting sign in when github is unconfigured switches to Token mode with an explanation`() = runTest {
        val recorder = Recorder()
        val authenticator = FakeAuthenticator(isConfigured = false)
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = FakeCredentialStore(),
            authenticator = authenticator,
            emit = recorder::emit,
        )
        controller.open(host = "github.com") {}

        controller.onStartGitHubSignIn()

        assertEquals(GitAuthMode.Token, recorder.latest.mode)
        assertEquals(
            "GitHub sign-in isn't configured on this build. Use an access token.",
            recorder.latest.error,
        )
        assertEquals(0, authenticator.authenticateCallCount)
    }

    @Test
    fun `device flow surfaces the user code and verification uri while awaiting authorization`() = runTest {
        val recorder = Recorder()
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = FakeCredentialStore(),
            authenticator = FakeAuthenticator(
                steps = flowOf(
                    GitHubDeviceAuthState.RequestingCode,
                    GitHubDeviceAuthState.AwaitingAuthorization("ABCD-1234", "https://github.com/login/device"),
                ),
            ),
            emit = recorder::emit,
        )
        controller.open(host = "github.com") {}

        controller.onStartGitHubSignIn()
        advanceUntilIdle()

        assertTrue(recorder.latest.isBusy)
        assertEquals("ABCD-1234", recorder.latest.device?.userCode)
        assertEquals("https://github.com/login/device", recorder.latest.device?.verificationUri)
        assertNull(recorder.latest.error)
    }

    @Test
    fun `device flow error clears busy and device state and surfaces the message`() = runTest {
        val recorder = Recorder()
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = FakeCredentialStore(),
            authenticator = FakeAuthenticator(
                steps = flowOf(
                    GitHubDeviceAuthState.RequestingCode,
                    GitHubDeviceAuthState.Error("access_denied"),
                ),
            ),
            emit = recorder::emit,
        )
        controller.open(host = "github.com") {}

        controller.onStartGitHubSignIn()
        advanceUntilIdle()

        assertFalse(recorder.latest.isBusy)
        assertNull(recorder.latest.device)
        assertEquals("access_denied", recorder.latest.error)
    }

    @Test
    fun `successful authorization marks success immediately but defers the retry by 1100ms`() = runTest {
        val recorder = Recorder()
        var retried = false
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = FakeCredentialStore(),
            authenticator = FakeAuthenticator(
                steps = flowOf(
                    GitHubDeviceAuthState.RequestingCode,
                    GitHubDeviceAuthState.Success("octocat"),
                ),
            ),
            emit = recorder::emit,
        )
        controller.open(host = "github.com") { retried = true }

        controller.onStartGitHubSignIn()
        runCurrent()

        assertTrue("success must be emitted at once, not after the delay", recorder.latest.succeeded)
        assertFalse(retried)

        // advanceTimeBy runs events in [now, now + n) — so at exactly 1100 the close is still pending.
        advanceTimeBy(1_100)
        assertFalse("retry must not fire before 1100ms elapses", retried)

        runCurrent()

        assertTrue("retry must fire at exactly 1100ms", retried)
        assertFalse(recorder.latest.visible)
        assertFalse(recorder.latest.succeeded)
    }

    /**
     * QUIRK, pinned deliberately: the re-entrancy guard in `onStartGitHubSignIn` keys off *emitted
     * state* (`isBusy` / `device` / `succeeded`), not off whether [GitAuthController] already has a
     * live `deviceJob`. Until the authenticator emits its first step, all three remain false, so a
     * second tap starts a second authentication and cancels the first. Any refactor of this boundary
     * must either preserve this or fix it knowingly — it is not currently guarded.
     */
    @Test
    fun `starting sign in twice before the authenticator emits starts a second device flow`() = runTest {
        val authenticator = FakeAuthenticator(steps = MutableSharedFlow())
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = FakeCredentialStore(),
            authenticator = authenticator,
            emit = {},
        )
        controller.open(host = "github.com") {}

        controller.onStartGitHubSignIn()
        advanceUntilIdle()
        controller.onStartGitHubSignIn()
        advanceUntilIdle()

        assertEquals(2, authenticator.authenticateCallCount)
    }

    @Test
    fun `starting sign in again after the code request is emitted is ignored`() = runTest {
        val authenticator = FakeAuthenticator(steps = flowOf(GitHubDeviceAuthState.RequestingCode))
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = FakeCredentialStore(),
            authenticator = authenticator,
            emit = {},
        )
        controller.open(host = "github.com") {}

        controller.onStartGitHubSignIn()
        advanceUntilIdle()
        controller.onStartGitHubSignIn()
        advanceUntilIdle()

        assertEquals(1, authenticator.authenticateCallCount)
    }

    /**
     * Uses a live [MutableSharedFlow] rather than `flowOf`, because a completed flow would let this
     * test pass even if `onAuthModeChanged` stopped cancelling `deviceJob`. Emitting *after* the mode
     * switch and asserting the state is unchanged is what actually proves the collector is dead.
     */
    @Test
    fun `switching to Token mode cancels the in-flight device flow and ignores late emissions`() = runTest {
        val steps = MutableSharedFlow<GitHubDeviceAuthState>()
        val recorder = Recorder()
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = FakeCredentialStore(),
            authenticator = FakeAuthenticator(steps = steps),
            emit = recorder::emit,
        )
        controller.open(host = "github.com") {}
        controller.onStartGitHubSignIn()
        advanceUntilIdle()
        steps.emit(GitHubDeviceAuthState.AwaitingAuthorization("ABCD-1234", "https://github.com/login/device"))
        advanceUntilIdle()
        assertEquals("ABCD-1234", recorder.latest.device?.userCode)

        controller.onAuthModeChanged(GitAuthMode.Token)
        val stateAfterSwitch = recorder.latest

        steps.emit(GitHubDeviceAuthState.Success("octocat"))
        advanceUntilIdle()

        assertEquals(GitAuthMode.Token, recorder.latest.mode)
        assertNull(recorder.latest.device)
        assertFalse(recorder.latest.isBusy)
        assertFalse("a cancelled device flow must not report success", recorder.latest.succeeded)
        assertEquals("late emission must not change state", stateAfterSwitch, recorder.latest)
    }

    // ---- dismissal ------------------------------------------------------------------------

    @Test
    fun `dismissing before success resets state and drops the pending retry`() = runTest {
        val recorder = Recorder()
        var retried = false
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = FakeCredentialStore(),
            authenticator = FakeAuthenticator(),
            emit = recorder::emit,
        )
        controller.open(host = "github.com") { retried = true }

        controller.onDismissAuthPrompt()

        assertFalse(recorder.latest.visible)
        assertNull(recorder.latest.host)
        assertFalse(retried)
    }

    @Test
    fun `dismissing after success runs the retry instead of discarding it`() = runTest {
        val recorder = Recorder()
        var retryCount = 0
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = FakeCredentialStore(),
            authenticator = FakeAuthenticator(
                steps = flowOf(GitHubDeviceAuthState.Success("octocat")),
            ),
            emit = recorder::emit,
        )
        controller.open(host = "github.com") { retryCount++ }
        controller.onStartGitHubSignIn()
        advanceTimeBy(1_000)

        controller.onDismissAuthPrompt()

        assertEquals(1, retryCount)
        assertFalse(recorder.latest.visible)

        // The deferred auto-close must not fire a second retry after an explicit dismiss.
        advanceUntilIdle()
        assertEquals(1, retryCount)
    }

    @Test
    fun `reopening replaces the pending retry without invoking the old one`() = runTest {
        var firstRetries = 0
        var secondRetries = 0
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = FakeCredentialStore(),
            authenticator = FakeAuthenticator(),
            emit = {},
        )

        controller.open(host = "a.com") { firstRetries++ }
        controller.open(host = "b.com") { secondRetries++ }
        controller.onAuthTokenChanged("t")
        controller.onSubmitAuthToken()

        assertEquals(0, firstRetries)
        assertEquals(1, secondRetries)
    }

    /**
     * Reopening while the post-success auto-close is still pending must cancel that scheduled job.
     * Without a live timer in play this would pass even if `open()` stopped calling `cancelJobs()`,
     * so the test deliberately reaches `Success` first and reopens inside the 1100 ms window.
     */
    @Test
    fun `reopening during the post-success delay cancels the scheduled close`() = runTest {
        val recorder = Recorder()
        var firstRetries = 0
        var secondRetries = 0
        val controller = GitAuthController(
            scope = controllerScope(),
            credentialStore = FakeCredentialStore(),
            authenticator = FakeAuthenticator(steps = flowOf(GitHubDeviceAuthState.Success("octocat"))),
            emit = recorder::emit,
        )
        controller.open(host = "a.com") { firstRetries++ }
        controller.onStartGitHubSignIn()
        advanceTimeBy(500)
        assertTrue(recorder.latest.succeeded)

        controller.open(host = "b.com") { secondRetries++ }
        advanceUntilIdle()

        assertEquals("the superseded auto-close must not fire", 0, firstRetries)
        assertEquals(0, secondRetries)
        assertTrue("the newly opened prompt must survive the old timer", recorder.latest.visible)
        assertEquals("b.com", recorder.latest.host)
    }
}
