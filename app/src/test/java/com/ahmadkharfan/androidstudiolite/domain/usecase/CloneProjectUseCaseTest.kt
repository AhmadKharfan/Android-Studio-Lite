package com.ahmadkharfan.androidstudiolite.domain.usecase

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.ahmadkharfan.androidstudiolite.data.local.AndroidProjectRepository
import com.ahmadkharfan.androidstudiolite.data.local.FileChangeBus
import com.ahmadkharfan.androidstudiolite.data.local.JGitGitRepository
import com.ahmadkharfan.androidstudiolite.domain.model.CloneOptions
import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.eclipse.jgit.api.Git
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CloneProjectUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `successful clone registers and returns an openable project`() = runTest {
        val source = seedRepo(temp.newFolder("sample"))
        val projectsDir = temp.newFolder("projects")
        val projectRepository = projectRepository("success-store", projectsDir)
        val useCase = CloneProjectUseCase(
            projectsDir = { projectsDir },
            gitRepository = gitRepository(),
            projectRepository = projectRepository,
        )

        val result = useCase.clone(source.toURI().toString(), CloneOptions(), credentials = null).toList().last()
        val project = projectRepository.openProject(result.clonedProjectId!!)

        assertEquals("sample", project.id)
        assertEquals(File(projectsDir, "sample").absolutePath, project.path)
        assertTrue(File(project.path, ".git").isDirectory)
    }

    @Test
    fun `registration failure keeps clone and reports recoverable error`() = runTest {
        val source = seedRepo(temp.newFolder("retained"))
        val projectsDir = temp.newFolder("failed-projects")
        val delegate = projectRepository("failure-store", projectsDir)
        val failingRepository = object : ProjectRepository by delegate {
            override suspend fun registerExistingProject(path: File): Project = error("store unavailable")
        }
        val useCase = CloneProjectUseCase(
            projectsDir = { projectsDir },
            gitRepository = gitRepository(),
            projectRepository = failingRepository,
        )

        val error = runCatching {
            useCase.clone(source.toURI().toString(), CloneOptions(), credentials = null).toList()
        }.exceptionOrNull()

        assertTrue(error is CloneRegistrationException)
        val cloneError = error as CloneRegistrationException
        assertEquals(File(projectsDir, "retained").absolutePath, cloneError.clonedDirectory.absolutePath)
        assertTrue(File(cloneError.clonedDirectory, ".git").isDirectory)
    }

    @Test
    fun `cancellation before registration deletes the unregistered clone`() = runTest {
        val projectsDir = temp.newFolder("cancelled-projects")
        val realGit = gitRepository()
        val blockingGit = object : com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository by realGit {
            override fun clone(
                url: String,
                destination: File,
                options: CloneOptions,
                credentials: GitCredentials?,
            ) = flow {
                destination.mkdirs()
                File(destination, "partial.pack").writeText("partial")
                emit(com.ahmadkharfan.androidstudiolite.domain.model.CloneProgress(0.25f, "Receiving"))
                awaitCancellation()
            }
        }
        val projectRepository = projectRepository("cancel-store", projectsDir)
        val useCase = CloneProjectUseCase({ projectsDir }, blockingGit, projectRepository)

        val job = launch {
            useCase.clone("https://example.com/cancelled.git", CloneOptions(), null).toList()
        }
        runCurrent()
        val destination = File(projectsDir, "cancelled")
        assertTrue(destination.exists())

        job.cancelAndJoin()

        assertTrue(!destination.exists())
        assertTrue(projectRepository.existing().none { it.path == destination.absolutePath })
    }

    private fun gitRepository() = JGitGitRepository(
        credentialStore = EmptyCredentialStore,
        io = Dispatchers.Unconfined,
    )

    private fun projectRepository(storeName: String, projectsRoot: File): AndroidProjectRepository {
        val dataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
            produceFile = { File(temp.newFolder(storeName), "recent.preferences_pb") },
        )
        return AndroidProjectRepository(
            projectsRoot = projectsRoot,
            dataStore = dataStore,
            changeBus = FileChangeBus(),
        )
    }

    private fun seedRepo(dir: File): File {
        Git.init().setDirectory(dir).call().use { git ->
            File(dir, "README.md").writeText("hello\n")
            git.add().addFilepattern("README.md").call()
            git.commit().setMessage("seed").setAuthor("t", "t@t").setCommitter("t", "t@t").call()
        }
        return dir
    }

    private object EmptyCredentialStore : GitCredentialStore {
        override fun credentialsForUrl(url: String): GitCredentials? = null
        override fun credentialsForHost(host: String): GitCredentials? = null
        override fun hasCredentials(host: String): Boolean = false
        override fun save(host: String, credentials: GitCredentials) = Unit
        override fun clear(host: String) = Unit
        override val changes = kotlinx.coroutines.flow.emptyFlow<Unit>()
    }
}
