package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import com.ahmadkharfan.androidstudiolite.feature.editor.git.history.GitHistoryViewModel
import java.io.File
import java.lang.reflect.Proxy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.parameter.parametersOf
import org.koin.dsl.module

/**
 * Regression: opening "History" without a file path passed `null` through `parametersOf`, and the
 * factory read it with `params.get<String>(1).takeIf { it.isNotBlank() }` — calling `isBlank()` on a
 * null crashed the app inside Koin's factory (Koin has no null-safe index read). The history route
 * now always supplies a non-null path (`path.orEmpty()`, empty = full history). This mirrors the
 * production factory and resolves the view model with the blank path the route sends for full
 * history and with a real file path.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GitHistoryViewModelFactoryTest {

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() {
        Dispatchers.resetMain()
        stopKoin()
    }

    @Test fun `history view model resolves with a null or blank path`() {
        val koin = startKoin {
            modules(
                module {
                    single { ProjectPathResolver(projectRepository = FakeProjectRepository) }
                    single { proxyGitRepository() }
                    // Mirrors gitModule's GitHistoryViewModel factory (JVM `factory`, not `viewModel`).
                    factory { params ->
                        GitHistoryViewModel(
                            projectId = params.get(0),
                            requestedPath = params.get<String>(1).takeIf { it.isNotBlank() },
                            projectPathResolver = get(),
                            gitRepository = get(),
                        )
                    }
                },
            )
        }.koin

        // Opened from the panel's "History" action: full history, empty path (never null).
        assertNotNull(koin.get<GitHistoryViewModel> { parametersOf("project", "") })
        // File history: a real path resolves as well.
        assertNotNull(koin.get<GitHistoryViewModel> { parametersOf("project", "app/src/Main.kt") })
    }

    private fun proxyGitRepository(): GitRepository =
        Proxy.newProxyInstance(
            GitRepository::class.java.classLoader,
            arrayOf(GitRepository::class.java),
        ) { _, _, _ -> null } as GitRepository

    private object FakeProjectRepository : ProjectRepository {
        override fun observeRecentProjects(): Flow<List<Project>> = MutableStateFlow(emptyList())
        override suspend fun createProject(spec: NewProjectSpec): Project = error("unused")
        override suspend fun registerExistingProject(path: File): Project = error("unused")
        override suspend fun openProject(id: String): Project = error("unused")
        override suspend fun deleteProject(id: String) = Unit
        override suspend fun renameProject(id: String, newName: String) = Unit
    }
}
