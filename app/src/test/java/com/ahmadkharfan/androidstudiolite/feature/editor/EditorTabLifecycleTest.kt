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
import com.ahmadkharfan.androidstudiolite.domain.repository.FileTreeRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreManager
import com.ahmadkharfan.androidstudiolite.domain.signing.ReleaseKeystoreParams
import com.ahmadkharfan.androidstudiolite.domain.signing.SigningConfig
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildNotifier
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildRunCoordinator
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class EditorTabLifecycleTest {
    @get:Rule val temp = TemporaryFolder()

    @Before fun setUp() = Dispatchers.setMain(Dispatchers.Unconfined)
    @After fun tearDown() = Dispatchers.resetMain()

    private lateinit var fileA: File
    private lateinit var fileB: File

    private fun viewModel(): EditorViewModel {
        val root = temp.newFolder("project")
        fileA = File(root, "A.kt").apply { writeText("aaa") }
        fileB = File(root, "B.kt").apply { writeText("bbb") }
        val tree = object : FileTreeRepository {
            override suspend fun getFileTree(projectId: String): List<FileNode> =
                listOf(FileNode(fileA.absolutePath, fileA.name), FileNode(fileB.absolutePath, fileB.name))
        }
        return EditorViewModel(
            projectId = "project",
            projectRepository = projectRepository(root),
            fileTreeRepository = tree,
            fileContentRepository = LocalFileContentRepository(FileChangeBus()),
            preferencesRepository = preferencesRepository(),
            gradleProjectReader = GradleProjectReader(),
            buildRunCoordinator = buildRunCoordinator(),
        )
    }

    @Test
    fun opensDefaultFileOnLoad() = runBlocking {
        val vm = viewModel()
        try {
            val state = withTimeout(5_000) { vm.state.first { it.tabs.isNotEmpty() } }
            assertEquals(listOf(fileA.absolutePath), state.tabs.map { it.id })
            assertEquals(fileA.absolutePath, state.activeTabId)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun openingASecondFileAddsAnActiveTabAndReopeningActivatesWithoutDuplicating() = runBlocking {
        val vm = viewModel()
        try {
            withTimeout(5_000) { vm.state.first { it.tabs.isNotEmpty() } }

            vm.onOpenFile(fileB.absolutePath, fileB.name)
            withTimeout(5_000) { vm.state.first { it.activeTabId == fileB.absolutePath } }
            assertEquals(listOf(fileA.absolutePath, fileB.absolutePath), vm.state.value.tabs.map { it.id })

            vm.onOpenFile(fileA.absolutePath, fileA.name)
            withTimeout(5_000) { vm.state.first { it.activeTabId == fileA.absolutePath } }
            assertEquals(2, vm.state.value.tabs.size)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun closingActiveTabRemovesItAndActivatesRemaining() = runBlocking {
        val vm = viewModel()
        try {
            withTimeout(5_000) { vm.state.first { it.tabs.isNotEmpty() } }
            vm.onOpenFile(fileB.absolutePath, fileB.name)
            withTimeout(5_000) { vm.state.first { it.tabs.size == 2 } }

            vm.onCloseTab(fileB.absolutePath)
            withTimeout(5_000) { vm.state.first { it.tabs.size == 1 } }
            assertEquals(listOf(fileA.absolutePath), vm.state.value.tabs.map { it.id })
            assertEquals(fileA.absolutePath, vm.state.value.activeTabId)
        } finally {
            vm.viewModelScope.cancel()
        }
    }

    @Test
    fun editingASessionMarksItsTabModified() = runBlocking {
        val vm = viewModel()
        try {
            withTimeout(5_000) { vm.state.first { it.tabs.isNotEmpty() } }
            val session = vm.sessionFor(fileA.absolutePath)!!
            session.replaceRange(0, session.text.length, "edited")
            vm.onSessionEdited(fileA.absolutePath)

            withTimeout(5_000) { vm.state.first { it.tabs.single().modified } }
            assertTrue(vm.state.value.tabs.single { it.id == fileA.absolutePath }.modified)
        } finally {
            vm.viewModelScope.cancel()
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
        override suspend fun setEditorFontFamily(family: String) = Unit
        override suspend fun setAccent(id: String) = Unit
        override suspend fun setAutoOpenLastProject(enabled: Boolean) = Unit
        override suspend fun ensureEditorThemeDefault(isDarkUi: Boolean) = Unit
        override suspend fun update(transform: (AppPreferences) -> AppPreferences) {
            preferences.value = transform(preferences.value)
        }
    }

    private fun buildRunCoordinator(): BuildRunCoordinator {
        val context = ContextWrapper(null)
        return BuildRunCoordinator(
            context = context,
            buildSystem = NoopBuildSystem,
            keystoreManager = UnusedKeystoreManager,
            apkInstaller = ApkInstaller(context),
            gradleReader = GradleProjectReader(),
            notifier = BuildNotifier(context),
            activeBuildStore = com.ahmadkharfan.androidstudiolite.data.remote.InMemoryActiveBuildStore(),
        )
    }

    private object NoopBuildSystem : BuildSystem {
        override suspend fun sync(projectRoot: File): ProjectModel = error("not used")
        override fun build(request: BuildRequest): Flow<BuildEvent> = emptyFlow()
        override fun attach(buildId: String, projectRoot: File): Flow<BuildEvent> = emptyFlow()
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
