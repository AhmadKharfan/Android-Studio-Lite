package com.ahmadkharfan.androidstudiolite.feature.editor

import androidx.lifecycle.viewModelScope
import android.content.ContextWrapper
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.ApkInstaller
import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import com.ahmadkharfan.androidstudiolite.data.local.FileChangeBus
import com.ahmadkharfan.androidstudiolite.data.local.LocalFileContentRepository
import com.ahmadkharfan.androidstudiolite.domain.model.AppPreferences
import com.ahmadkharfan.androidstudiolite.domain.model.AppThemeMode
import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import com.ahmadkharfan.androidstudiolite.domain.model.RootInvalidationReason
import com.ahmadkharfan.androidstudiolite.domain.repository.FileTreeRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreManager
import com.ahmadkharfan.androidstudiolite.domain.signing.ReleaseKeystoreParams
import com.ahmadkharfan.androidstudiolite.domain.signing.SigningConfig
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildNotifier
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildRunCoordinator
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EditorRootInvalidationTest {
    @get:Rule val temp = TemporaryFolder()

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `root invalidation reloads clean tabs and preserves dirty buffers`() = runBlocking {
        val root = temp.newFolder("project")
        val source = File(root, "source.txt").apply { writeText("one") }
        val bus = FileChangeBus()
        val treeReads = AtomicInteger()
        val tree = object : FileTreeRepository {
            override suspend fun getFileTree(projectId: String): List<FileNode> {
                treeReads.incrementAndGet()
                return listOf(FileNode(source.absolutePath, source.name))
            }
        }
        val viewModel = EditorViewModel(
            projectId = "project",
            projectRepository = projectRepository(root),
            fileTreeRepository = tree,
            fileContentRepository = LocalFileContentRepository(bus),
            preferencesRepository = preferencesRepository(),
            gradleProjectReader = GradleProjectReader(),
            buildRunCoordinator = buildRunCoordinator(),
        )
        try {
            withTimeout(5_000) { viewModel.state.first { it.tabs.isNotEmpty() } }

            source.writeText("two")
            bus.emitRootInvalidated(root.absolutePath, RootInvalidationReason.GIT_OPERATION)
            val reloaded = withTimeout(5_000) {
                viewModel.state.first { it.tabs.singleOrNull()?.text == "two" }
            }
            assertEquals("two", reloaded.tabs.single().text)
            assertTrue(treeReads.get() >= 2)

            val session = viewModel.sessionFor(source.absolutePath)!!
            session.replaceRange(0, session.text.length, "unsaved")
            viewModel.onSessionEdited(source.absolutePath)
            source.writeText("three")
            bus.emitRootInvalidated(root.absolutePath, RootInvalidationReason.GIT_OPERATION)
            withTimeout(5_000) {
                viewModel.state.first { it.snackbarMessage.orEmpty().contains("unsaved edits were kept") }
            }

            assertEquals("unsaved", viewModel.sessionFor(source.absolutePath)!!.text)
            assertTrue(viewModel.state.value.tabs.single().modified)
            assertTrue(viewModel.state.value.snackbarMessage.orEmpty().contains("unsaved edits were kept"))
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private fun projectRepository(root: File) = object : ProjectRepository {
        private val project = Project(
            id = "project",
            name = "Project",
            path = root.absolutePath,
            language = "Text",
            lastOpenedMillis = 0L,
        )
        override fun observeRecentProjects(): Flow<List<Project>> = MutableStateFlow(listOf(project))
        override suspend fun createProject(spec: NewProjectSpec): Project = error("not used")
        override suspend fun registerExistingProject(path: File): Project = error("not used")
        override suspend fun openProject(id: String): Project = project
        override suspend fun deleteProject(id: String) = Unit
        override suspend fun renameProject(id: String, newName: String) = Unit
    }

    private fun preferencesRepository() = object : PreferencesRepository {
        private val preferences = MutableStateFlow(AppPreferences(editorAutoSave = false))
        override fun observePreferences(): Flow<AppPreferences> = preferences
        override suspend fun setThemeMode(mode: AppThemeMode) = Unit
        override suspend fun setEditorFontSize(size: Int) = Unit
        override suspend fun setEditorTheme(id: String) = Unit
        override suspend fun setAccent(id: String) = Unit
        override suspend fun setLanguage(language: String) = Unit
        override suspend fun setAutoOpenLastProject(enabled: Boolean) = Unit
        override suspend fun setSnowfallEasterEgg(enabled: Boolean) = Unit
        override suspend fun update(transform: (AppPreferences) -> AppPreferences) {
            preferences.value = transform(preferences.value)
        }
    }

    private fun buildRunCoordinator(): BuildRunCoordinator {
        val context = ContextWrapper(null)
        val gradleReader = GradleProjectReader()
        return BuildRunCoordinator(
            context = context,
            buildSystem = NoopBuildSystem,
            keystoreManager = UnusedKeystoreManager,
            apkInstaller = ApkInstaller(context),
            gradleReader = gradleReader,
            notifier = BuildNotifier(context),
        )
    }

    private object NoopBuildSystem : BuildSystem {
        override suspend fun sync(projectRoot: File): ProjectModel = error("not used")
        override fun build(request: BuildRequest): Flow<BuildEvent> = emptyFlow()
        override fun cancel() = Unit
    }

    private object UnusedKeystoreManager : KeystoreManager {
        override fun debugKeystoreFile(): File = File("debug.keystore")
        override fun suggestedReleaseKeystoreFile(): File = File("release.jks")
        override suspend fun debugSigningConfig(): SigningConfig = error("not used")
        override suspend fun createReleaseKeystore(params: ReleaseKeystoreParams): SigningConfig = error("not used")
        override suspend fun importReleaseKeystore(
            storeFile: File,
            storePassword: String,
            keyAlias: String,
            keyPassword: String,
        ): SigningConfig = error("not used")
        override suspend fun releaseSigningConfig(): SigningConfig? = null
        override suspend fun clearReleaseKeystore() = Unit
        override suspend fun signingConfigFor(buildType: String): SigningConfig = error("not used")
    }
}
