package com.ahmadkharfan.androidstudiolite.domain.usecase

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.ahmadkharfan.androidstudiolite.data.local.AndroidProjectRepository
import com.ahmadkharfan.androidstudiolite.data.local.FileChangeBus
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ProjectPathResolverTest {

    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun `resolver returns registered custom project path`() = runBlocking {
        val projectsRoot = temp.newFolder("projects")
        val customProject = temp.newFolder("custom", "repo")
        val repository = AndroidProjectRepository(
            projectsRoot = projectsRoot,
            dataStore = PreferenceDataStoreFactory.create(
                produceFile = { File(temp.newFolder("resolver-store"), "recent.preferences_pb") },
            ),
            changeBus = FileChangeBus(),
        )
        val project = repository.registerExistingProject(customProject)

        val resolved = ProjectPathResolver(repository)(project.id)

        assertEquals(customProject.absolutePath, resolved.absolutePath)
    }
}
