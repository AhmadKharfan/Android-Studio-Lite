package com.ahmadkharfan.androidstudiolite.data.githubactions.build

import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient
import com.ahmadkharfan.androidstudiolite.data.githubactions.snapshot.SourceSnapshotPusher
import com.ahmadkharfan.androidstudiolite.data.githubactions.workflow.BuildWorkflow
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent.RemoteBuildPhase
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildReadiness
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildReadiness
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildState
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.GradleProjectInspector
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.GradleProjectSummary
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import com.ahmadkharfan.androidstudiolite.domain.signing.ApkSigner
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreError
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreException
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreManager
import com.ahmadkharfan.androidstudiolite.domain.signing.ReleaseKeystoreParams
import com.ahmadkharfan.androidstudiolite.domain.signing.SigningConfig
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.eclipse.jgit.api.Git
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GitHubActionsBuildSystemTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val github = FakeGitHub()
    private lateinit var project: File
    private lateinit var gitRoot: File
    private var token: String? = "gho_test"
    private var now = 0L

    @Before
    fun setUp() {
        github.start()
        project = tmp.newFolder("MyApp")
        File(project, "settings.gradle.kts").writeText("include(\":app\")")
        File(project, "gradlew").writeText("#!/bin/sh")
        File(project, "app").mkdirs()
        File(project, "app/build.gradle.kts").writeText("plugins {}")
        gitRoot = tmp.newFolder("git")
        Git.init().setBare(true).setDirectory(File(gitRoot, "octo/asl-build.git")).call().close()
    }

    @After
    fun tearDown() {
        github.server.shutdown()
    }

    private var signing: ApkSigning? = null
    private var repositorySetup: RepositorySetup = RepositorySetup.CreateIfMissing
    private var signInMessage = GitHubBuildMessages.SIGN_IN_REQUIRED

    private fun buildSystem(): GitHubActionsBuildSystem {
        val api = GitHubApiClient(token = { token }, baseUrl = github.server.url("/"), waitBeforeRetry = {})
        return GitHubActionsBuildSystem(
            api = api,
            token = { token },
            snapshots = SourceSnapshotPusher(File(tmp.root, "shadow")),
            inspector = Inspector,
            config = GitHubActionsConfig(
                File(tmp.root, "downloads"),
                gitBaseUrl = gitRoot.toURI().toString(),
                repositorySetup = repositorySetup,
                signInMessage = signInMessage,
            ),
            signing = signing,
            seams = GitHubActionsSeams(
                clock = { now },
                ids = { FakeGitHub.CORRELATION },
                wait = { now += it },
                cancelScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined),
            ),
        )
    }

    private val request get() = BuildRequest(project, ":app", "debug", taskPath = ":app:assembleDebug")

    private fun build(): List<BuildEvent> = runBlocking { buildSystem().build(request).toList() }

    @Test
    fun `first build provisions the repository, uploads, dispatches and returns the verified apk`() {
        val events = build()

        assertTrue(github.paths().contains("POST /user/repos"))
        assertTrue(github.paths().contains("PUT /repos/octo/asl-build/contents/.github/workflows/asl-build.yml"))
        val finished = events.last() as BuildEvent.Finished
        assertTrue(events.toString(), finished.success)
        val artifact = events.filterIsInstance<BuildEvent.ArtifactProduced>().single()
        assertEquals(FakeGitHub.APK_BYTES.toList(), artifact.file.readBytes().toList())
        assertEquals(BuildEvent.ArtifactKind.APK, artifact.kind)
        assertEquals(FakeGitHub.sha256(FakeGitHub.APK_BYTES), artifact.sha256)
    }

    @Test
    fun `events describe the whole lifecycle in order`() {
        val events = build()

        assertTrue(events.first() is BuildEvent.Started)
        val phases = events.filterIsInstance<BuildEvent.StatusChanged>().map { it.phase }
        assertEquals(
            listOf(
                RemoteBuildPhase.PREPARING,
                RemoteBuildPhase.UPLOADING,
                RemoteBuildPhase.QUEUED,
                RemoteBuildPhase.RUNNING,
                RemoteBuildPhase.DOWNLOADING,
            ),
            phases,
        )
        assertTrue(events.contains(BuildEvent.Progress("Running Gradle…")))
        assertTrue(events.contains(BuildEvent.TaskFinished(":app:compileDebugKotlin", BuildEvent.TaskResult.SUCCESS)))
        assertTrue(events.contains(BuildEvent.Output("BUILD SUCCESSFUL", BuildEvent.OutputStream.STDOUT)))
    }

    @Test
    fun `build ids can be re-attached and carry the run id once known`() {
        val bound = build().filterIsInstance<BuildEvent.RemoteBuildBound>().map { BuildHandle.decode(it.buildId) }

        assertEquals(
            listOf(
                BuildHandle("octo", "asl-build", FakeGitHub.CORRELATION, null),
                BuildHandle("octo", "asl-build", FakeGitHub.CORRELATION, 77),
            ),
            bound,
        )
    }

    @Test
    fun `dispatch carries the snapshot that was pushed and the exact task`() {
        build()

        val remote = Git.open(File(gitRoot, "octo/asl-build.git"))
        val branch = remote.use { git -> git.repository.refDatabase.getRefsByPrefix("refs/heads/asl/src/").single() }
        val body = github.bodyOf("POST /repos/octo/asl-build/actions/workflows/asl-build.yml/dispatches")
        assertTrue(body, body.contains("\"ref\":\"main\""))
        assertTrue(body, body.contains("\"source_ref\":\"${branch.name.removePrefix("refs/heads/")}\""))
        assertTrue(body, body.contains("\"source_sha\":\"${branch.objectId.name}\""))
        assertTrue(body, body.contains("\"tasks\":\":app:assembleDebug\""))
        assertTrue(body, body.contains("\"java_version\":\"17\""))
    }

    @Test
    fun `an up to date repository is used as is`() {
        github.repositoryExists = true
        github.workflowContent = BuildWorkflow.contents()

        build()

        assertFalse(github.paths().contains("POST /user/repos"))
        assertFalse(github.paths().any { it.startsWith("PUT ") })
    }

    @Test
    fun `a newer workflow written by a newer app is not downgraded`() {
        github.repositoryExists = true
        github.workflowContent = "# asl-workflow-version: ${BuildWorkflow.VERSION + 1}\nname: newer"

        build()

        assertFalse(github.paths().any { it.startsWith("PUT ") })
    }

    @Test
    fun `a public build repository is refused before any source is pushed`() {
        github.repositoryExists = true
        github.repositoryPrivate = false

        val events = build()

        val problem = events.filterIsInstance<BuildEvent.Problem>().single()
        assertTrue(problem.message, problem.message.contains("is public"))
        assertFalse((events.last() as BuildEvent.Finished).success)
        assertFalse(github.paths().any { it.contains("dispatches") })
    }

    @Test
    fun `run is found through its name when dispatch doesn't return it`() {
        github.dispatchReturnsRunId = false

        val events = build()

        assertTrue(github.paths().any { it.startsWith("GET /repos/octo/asl-build/actions/workflows/asl-build.yml/runs") })
        assertTrue((events.last() as BuildEvent.Finished).success)
    }

    @Test
    fun `failed build reports gradle's reason and no artifact`() {
        github.conclusion = "failure"
        github.resultZip = FakeGitHub.resultZip(
            success = false,
            log = "e: file:///home/runner/work/asl-build/asl-build/app/src/main/java/MainActivity.kt:20:9 " +
                "Unresolved reference 'foo'.\n\n* What went wrong:\nExecution failed for task ':app:compileDebugKotlin'.\n> Compilation error\n\n* Try:",
        )

        val events = build()

        val problems = events.filterIsInstance<BuildEvent.Problem>().map { it.message }
        assertTrue(problems.toString(), problems.first().startsWith("Gradle: Execution failed for task ':app:compileDebugKotlin'."))
        val located = events.filterIsInstance<BuildEvent.Problem>().single { it.file != null }
        assertEquals(File(project, "app/src/main/java/MainActivity.kt"), located.file)
        assertEquals(20, located.line)
        assertEquals(9, located.column)
        assertEquals("Unresolved reference 'foo'.", located.message)
        assertTrue(events.none { it is BuildEvent.ArtifactProduced })
        assertFalse((events.last() as BuildEvent.Finished).success)
    }

    @Test
    fun `run that never produced a result explains the conclusion`() {
        github.conclusion = "startup_failure"
        github.resultZip = null

        val events = build()

        val problem = events.filterIsInstance<BuildEvent.Problem>().single()
        assertTrue(problem.message, problem.message.startsWith("GitHub couldn't start the build workflow."))
    }

    @Test
    fun `tampered apk is rejected`() {
        github.resultZip = FakeGitHub.resultZip(apkSha = "0".repeat(64))

        val events = build()

        assertTrue(events.none { it is BuildEvent.ArtifactProduced })
        assertFalse((events.last() as BuildEvent.Finished).success)
    }

    @Test
    fun `result entries outside the result folder are refused`() {
        github.resultZip = FakeGitHub.resultZip(extraEntry = "../../evil.txt")

        val events = build()

        assertFalse((events.last() as BuildEvent.Finished).success)
        assertFalse(File(tmp.root, "evil.txt").exists())
    }

    @Test
    fun `signed out builds fail before touching github`() {
        token = null

        val events = build()

        assertFalse((events.last() as BuildEvent.Finished).success)
        assertEquals(0, github.server.requestCount)
    }

    @Test
    fun `attach resumes a build that died before its run id was known`() {
        github.repositoryExists = true
        github.workflowContent = BuildWorkflow.contents()
        github.runStatuses = ArrayDeque(listOf("in_progress", "completed"))

        val events = runBlocking {
            buildSystem().attach(BuildHandle("octo", "asl-build", FakeGitHub.CORRELATION).encode(), project).toList()
        }

        assertNotNull(events.filterIsInstance<BuildEvent.ArtifactProduced>().singleOrNull())
        assertTrue((events.last() as BuildEvent.Finished).success)
    }

    @Test
    fun `cancel stops the run on github`() {
        val system = buildSystem()
        github.runStatuses = ArrayDeque(listOf("in_progress", "in_progress", "completed"))

        runBlocking {
            system.build(request).collect { event ->
                if (event is BuildEvent.RemoteBuildBound && BuildHandle.decode(event.buildId)?.runId != null) system.cancel()
            }
        }

        // The cancel request is sent from a background scope; give it a moment to arrive.
        val cancel = "POST /repos/octo/asl-build/actions/runs/77/cancel"
        val deadline = System.currentTimeMillis() + 5_000
        while (cancel !in github.paths() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue(github.paths().toString(), cancel in github.paths())
    }

    @Test
    fun `a cancelled run that finishes anyway has its result deleted`() {
        val system = buildSystem()
        github.runStatuses = ArrayDeque(listOf("in_progress", "in_progress", "in_progress", "completed"))
        github.conclusion = "cancelled"

        runBlocking {
            // As the coordinator does: cancel the build, then stop collecting it.
            val collecting = launch {
                system.build(request).collect { event ->
                    if (event is BuildEvent.RemoteBuildBound && BuildHandle.decode(event.buildId)?.runId != null) {
                        system.cancel()
                        cancel()
                    }
                }
            }
            collecting.join()
        }

        val delete = "DELETE /repos/octo/asl-build/actions/artifacts/9"
        val deadline = System.currentTimeMillis() + 5_000
        while (delete !in github.paths() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertTrue(github.paths().toString(), delete in github.paths())
        assertEquals(1, github.paths().count { it == "POST /repos/octo/asl-build/actions/runs/77/cancel" })
        assertFalse(github.paths().any { it.startsWith("GET /repos/octo/asl-build/actions/artifacts/9/zip") })
    }

    @Test
    fun `readiness asks for sign-in or the missing workflow scope`() = runBlocking {
        token = null
        assertEquals(BuildReadiness.NeedsSignIn(GitHubBuildMessages.SIGN_IN_REQUIRED), buildSystem().readiness())

        token = "gho_test"
        github.scopes = "repo"
        assertEquals(BuildReadiness.NeedsSignIn(GitHubBuildMessages.WORKFLOW_SCOPE_MISSING), buildSystem().readiness())

        github.scopes = "repo, workflow"
        assertEquals(BuildReadiness.Ready, buildSystem().readiness())
    }

    @Test
    fun `with the github app a missing repository is set up by the user, never created`() {
        repositorySetup = RepositorySetup.Manual("https://github.com/apps/asl/installations/new")

        val events = build()

        val problem = events.filterIsInstance<BuildEvent.Problem>().single()
        assertTrue(problem.message, problem.message.contains("https://github.com/apps/asl/installations/new"))
        assertFalse((events.last() as BuildEvent.Finished).success)
        assertFalse(github.paths().contains("POST /user/repos"))
        assertFalse(github.paths().any { it.contains("dispatches") })
    }

    @Test
    fun `with the github app readiness asks for app sign-in, then access, then the repository`() = runBlocking {
        val setup = RepositorySetup.Manual(installUrl = null)
        repositorySetup = setup
        signInMessage = "Connect GitHub for builds."
        github.scopes = null

        token = null
        assertEquals(BuildReadiness.NeedsSignIn("Connect GitHub for builds."), buildSystem().readiness())

        token = "ghu_test"
        github.appInstalled = false
        assertEquals(BuildReadiness.NeedsSetup(setup.message("asl-build")), buildSystem().readiness())

        github.appInstalled = true
        val unreachable = buildSystem().readiness() as BuildReadiness.NeedsSetup
        assertTrue(unreachable.reason, unreachable.reason.contains(FakeGitHub.INSTALLATION_URL))

        github.repositoryExists = true
        assertEquals(BuildReadiness.Ready, buildSystem().readiness())
    }

    @Test
    fun `with the github app a public repository blocks the build up front`() = runBlocking {
        repositorySetup = RepositorySetup.Manual(installUrl = null)
        github.repositoryExists = true
        github.repositoryPrivate = false

        val readiness = buildSystem().readiness() as BuildReadiness.NeedsSetup
        assertTrue(readiness.reason, readiness.reason.contains("is public"))
    }

    @Test
    fun `readiness answers through the shared readiness when one is given`() = runBlocking {
        val shared = object : CloudBuildReadiness {
            override val state = MutableStateFlow<CloudBuildState?>(null)
            override suspend fun check(): CloudBuildState = CloudBuildState.Offline.also { state.value = it }
        }
        val system = GitHubActionsBuildSystem(
            api = GitHubApiClient(token = { token }, baseUrl = github.server.url("/"), waitBeforeRetry = {}),
            token = { token },
            snapshots = SourceSnapshotPusher(File(tmp.root, "shadow")),
            inspector = Inspector,
            config = GitHubActionsConfig(File(tmp.root, "downloads")),
            readiness = GitHubActionsReadiness(shared = shared),
        )

        assertTrue(system.readiness() is BuildReadiness.Unavailable)
        assertEquals(CloudBuildState.Offline, shared.state.value)
        assertEquals(0, github.server.requestCount)
    }

    @Test
    fun `fine-grained tokens without scope reporting are ready`() = runBlocking {
        github.scopes = null

        assertEquals(BuildReadiness.Ready, buildSystem().readiness())
    }

    @Test
    fun `github app user tokens report an empty scope header and still build`() {
        github.scopes = ""

        runBlocking { assertEquals(BuildReadiness.Ready, buildSystem().readiness()) }
        assertTrue((build().last() as BuildEvent.Finished).success)
    }

    @Test
    fun `live output streams while the build runs and is not repeated at the end`() {
        github.runStatuses = ArrayDeque(listOf("queued", "in_progress", "in_progress", "completed"))
        github.liveOutputs = ArrayDeque(listOf(2 to "a\nb\n", 4 to "a\nb\nc\nd\n"))
        github.resultZip = FakeGitHub.resultZip(log = "a\nb\nc\nd\ne")

        val events = build()

        val lines = events.filterIsInstance<BuildEvent.Output>().map { it.line }
        assertEquals(listOf("a", "b", "c", "d", "e"), lines)
        val firstLine = events.indexOfFirst { it is BuildEvent.Output }
        val download = events.indexOfFirst { it is BuildEvent.StatusChanged && it.phase == RemoteBuildPhase.DOWNLOADING }
        assertTrue("live output came after the download", firstLine < download)
    }

    @Test
    fun `a gap in live output is announced and the full log follows`() {
        github.runStatuses = ArrayDeque(listOf("in_progress", "completed"))
        github.liveOutputs = ArrayDeque(listOf(10 to "i\nj\n"))
        github.resultZip = FakeGitHub.resultZip(log = "a\nb")

        val lines = build().filterIsInstance<BuildEvent.Output>().map { it.line }

        assertTrue(lines.toString(), lines.first().startsWith("… 8 lines not shown live"))
        assertEquals(listOf("i", "j", "── Full build log ──", "a", "b"), lines.drop(1))
    }

    @Test
    fun `apks are re-signed with the device debug key`() {
        val signer = RecordingSigner()
        signing = ApkSigning(signer, Keystores)

        val artifact = build().filterIsInstance<BuildEvent.ArtifactProduced>().single()

        assertEquals(listOf("debug"), signer.buildTypes)
        assertEquals(true, artifact.signed)
        assertEquals("cert-sha", artifact.certificateSha256)
        assertEquals("signed:fake-apk-bytes", artifact.file.readText())
        assertEquals(FakeGitHub.sha256(artifact.file.readBytes()), artifact.sha256)
    }

    @Test
    fun `release builds are signed with the release keystore`() {
        val signer = RecordingSigner()
        signing = ApkSigning(signer, Keystores)

        runBlocking { buildSystem().build(request.copy(variantName = "release", buildType = "release")).toList() }

        assertEquals(listOf("release"), signer.buildTypes)
    }

    @Test
    fun `release app bundles are signed on the device with the release keystore`() {
        val signer = RecordingSigner()
        signing = ApkSigning(signer, Keystores)
        github.resultZip = FakeGitHub.resultZip(artifactName = "app-release.aab")

        val events = runBlocking {
            buildSystem().build(request.copy(variantName = "release", buildType = "release", kind = BuildKind.BUNDLE)).toList()
        }

        val artifact = events.filterIsInstance<BuildEvent.ArtifactProduced>().single()
        assertEquals(listOf("bundle:release"), signer.buildTypes)
        assertEquals(BuildEvent.ArtifactKind.AAB, artifact.kind)
        assertEquals(true, artifact.signed)
        assertEquals("bundle-signed:fake-apk-bytes", artifact.file.readText())
    }

    @Test
    fun `a signing failure fails the build instead of installing a foreign signature`() {
        signing = ApkSigning(RecordingSigner(fail = true), Keystores)

        val events = build()

        assertTrue(events.none { it is BuildEvent.ArtifactProduced })
        val problem = events.filterIsInstance<BuildEvent.Problem>().single()
        assertTrue(problem.message, problem.message.startsWith("Couldn't sign the APK on this device"))
        assertFalse((events.last() as BuildEvent.Finished).success)
    }

    private class RecordingSigner(private val fail: Boolean = false) : ApkSigner {
        val buildTypes = mutableListOf<String>()

        override suspend fun sign(input: File, output: File, config: SigningConfig): String {
            buildTypes += config.keyAlias
            if (fail) throw KeystoreException(KeystoreError.WrongKeyPassword)
            output.parentFile?.mkdirs()
            output.writeText("signed:" + input.readText())
            return "cert-sha"
        }

        override suspend fun signBundle(input: File, output: File, config: SigningConfig): String {
            buildTypes += "bundle:" + config.keyAlias
            output.parentFile?.mkdirs()
            output.writeText("bundle-signed:" + input.readText())
            return "cert-sha"
        }
    }

    private object Keystores : KeystoreManager {
        private fun config(type: String) = SigningConfig(File(type), "pw", type, "pw", isDebug = type == "debug")
        override suspend fun signingConfigFor(buildType: String) = config(buildType)
        override suspend fun debugSigningConfig() = config("debug")
        override suspend fun releaseSigningConfig() = config("release")
        override fun debugKeystoreFile() = File("debug")
        override fun suggestedReleaseKeystoreFile() = File("release")
        override suspend fun createReleaseKeystore(params: ReleaseKeystoreParams) = config("release")
        override suspend fun importReleaseKeystore(storeFile: File, storePassword: String, keyAlias: String, keyPassword: String) =
            config("release")
        override suspend fun clearReleaseKeystore() = Unit
    }

    private object Inspector : GradleProjectInspector {
        override fun isGradleProject(dir: File) = true
        override fun inspect(projectRoot: File) = GradleProjectSummary(ProjectModel("MyApp", projectRoot, emptyList()))
    }
}
