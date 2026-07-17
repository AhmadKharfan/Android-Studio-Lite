package com.ahmadkharfan.androidstudiolite.feature.createproject

import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.Project
import com.ahmadkharfan.androidstudiolite.domain.model.ProjectTemplate
import com.ahmadkharfan.androidstudiolite.domain.repository.ProjectRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.TemplateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class CreateProjectViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private val templates = FakeTemplateRepository()
    private val projects = FakeProjectRepository()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = CreateProjectViewModel(
        templateRepository = templates,
        projectRepository = projects,
        defaultLocation = DEFAULT_LOCATION,
        dispatcher = dispatcher,
    )

    @Test
    fun `defaults to the empty activity compose template, not the first one loaded`() {
        assertEquals("no-activity", templates.templates.first().id) // the trap the old code fell into
        assertEquals(DEFAULT_TEMPLATE_ID, viewModel().state.value.selectedTemplateId)
    }

    @Test
    fun `package auto-derives from the name until the user edits it`() {
        val vm = viewModel()

        vm.onNameChanged("Weather")
        assertEquals("com.example.weather", vm.state.value.packageName)

        vm.onPackageChanged("io.acme.weather")
        vm.onNameChanged("WeatherPro")

        assertEquals("WeatherPro", vm.state.value.projectName)
        assertEquals("io.acme.weather", vm.state.value.packageName)
    }

    @Test
    fun `an invalid package blocks create`() {
        val vm = viewModel()

        vm.onPackageChanged("Com.Example")
        assertNotNull(vm.state.value.packageError)
        assertFalse(vm.state.value.canCreate)

        vm.onPackageChanged("com.example.ok")
        assertNull(vm.state.value.packageError)
    }

    @Test
    fun `a duplicate name blocks create`() {
        projects.projects.value = listOf(project(name = "Taken", packageName = "com.example.taken"))
        val vm = viewModel()

        vm.onNameChanged("Taken")

        assertEquals("A project named \"Taken\" already exists", vm.state.value.nameError)
        assertFalse(vm.state.value.canCreate)
    }

    @Test
    fun `a duplicate package blocks create even under a different name`() {
        projects.projects.value = listOf(project(name = "Taken", packageName = "com.example.taken"))
        val vm = viewModel()

        vm.onNameChanged("Fresh")
        vm.onPackageChanged("com.example.taken")

        assertNull(vm.state.value.nameError)
        assertEquals("Another project already uses com.example.taken", vm.state.value.packageError)
        assertFalse(vm.state.value.canCreate)
    }

    @Test
    fun `create passes the chosen save location through to the spec`() {
        val vm = viewModel()

        vm.onNameChanged("Placed")
        vm.onLocationChanged("/sdcard/Documents/code")
        vm.onCreateProject()

        assertEquals("/sdcard/Documents/code", projects.created?.saveLocation)
        assertEquals("Placed", projects.created?.name)
        assertEquals(DEFAULT_TEMPLATE_ID, projects.created?.templateId)
    }

    @Test
    fun `create falls back to the default projects root`() {
        val vm = viewModel()

        vm.onCreateProject()

        assertEquals(DEFAULT_LOCATION, projects.created?.saveLocation)
    }

    @Test
    fun `create is refused while a field is invalid`() {
        val vm = viewModel()

        vm.onPackageChanged("nope")
        vm.onCreateProject()

        assertNull(projects.created)
        assertFalse(vm.state.value.creating)
    }

    @Test
    fun `a failed create releases the button instead of spinning forever`() {
        projects.failWith = IllegalStateException("disk full")
        val vm = viewModel()

        vm.onCreateProject()

        assertFalse(vm.state.value.creating)
        assertEquals("disk full", vm.state.value.nameError)
    }

    private fun project(name: String, packageName: String) = Project(
        id = name.lowercase(),
        name = name,
        path = "/projects/${name.lowercase()}",
        language = "Kotlin",
        lastOpenedMillis = null,
        packageName = packageName,
    )

    private class FakeTemplateRepository : TemplateRepository {
        // Deliberately ordered so "first template" != the expected default.
        val templates = listOf(
            ProjectTemplate("no-activity", "No Activity", "", "template_no_activity", emptyList()),
            ProjectTemplate(DEFAULT_TEMPLATE_ID, "Jetpack Compose", "", "template_compose", emptyList()),
            ProjectTemplate("empty-views", "Empty project", "", "template_empty_activity", emptyList()),
        )

        override suspend fun getTemplates(): List<ProjectTemplate> = templates
    }

    private class FakeProjectRepository : ProjectRepository {
        val projects = MutableStateFlow<List<Project>>(emptyList())
        var created: NewProjectSpec? = null
        var failWith: Throwable? = null

        override fun observeRecentProjects(): Flow<List<Project>> = projects

        override suspend fun createProject(spec: NewProjectSpec): Project {
            failWith?.let { throw it }
            created = spec
            return Project(spec.name.lowercase(), spec.name, "/p", "Kotlin", null, spec.packageName)
        }

        override suspend fun registerExistingProject(path: File): Project = error("unused")
        override suspend fun openProject(id: String): Project = error("unused")
        override suspend fun deleteProject(id: String) = Unit
        override suspend fun renameProject(id: String, newName: String) = Unit
    }

    private companion object {
        const val DEFAULT_LOCATION = "/data/user/0/app/files/home/AndroidStudioLiteProjects"
    }
}
