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


        assertNotNull(koin.get<GitHistoryViewModel> { parametersOf("project", "") })

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
