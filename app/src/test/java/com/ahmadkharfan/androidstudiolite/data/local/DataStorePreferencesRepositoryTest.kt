package com.ahmadkharfan.androidstudiolite.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import com.ahmadkharfan.androidstudiolite.domain.model.AppPreferences
import com.ahmadkharfan.androidstudiolite.domain.model.AppThemeMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DataStorePreferencesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var file: File

    @Before
    fun setUp() {
        file = tmp.newFile("app_preferences.preferences_pb").also { it.delete() }
    }

    private fun <T> withRepository(block: suspend (DataStorePreferencesRepository) -> T): T {
        val scope = CoroutineScope(SupervisorJob())
        try {
            val store: DataStore<Preferences> =
                PreferenceDataStoreFactory.create(scope = scope) { file }
            return runBlocking { block(DataStorePreferencesRepository(store)) }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `defaults are returned when nothing has been written`() {
        val prefs = withRepository { it.observePreferences().first() }
        assertEquals(AppPreferences(), prefs)
    }

    @Test
    fun `dedicated setters persist across a simulated relaunch`() {
        withRepository { repo ->
            repo.setThemeMode(AppThemeMode.DARK)
            repo.setEditorFontSize(21)
            repo.setEditorTheme("solarized")
            repo.setAccent("crimson")
            repo.setAutoOpenLastProject(false)
        }

        val reloaded = withRepository { it.observePreferences().first() }

        assertEquals(AppThemeMode.DARK, reloaded.themeMode)
        assertEquals(21, reloaded.editorFontSize)
        assertEquals("solarized", reloaded.editorThemeId)
        assertEquals("crimson", reloaded.accentId)
        assertEquals(false, reloaded.autoOpenLastProject)
    }

    @Test
    fun `selected variant is remembered per project across a simulated relaunch`() {
        withRepository { repo ->
            repo.setSelectedVariant("proj-a", "fdroidRelease")
            repo.setSelectedVariant("proj-b", "gplayDebug")
        }

        withRepository { repo ->
            assertEquals("fdroidRelease", repo.getSelectedVariant("proj-a"))
            assertEquals("gplayDebug", repo.getSelectedVariant("proj-b"))
            assertEquals(null, repo.getSelectedVariant("proj-unknown"))
        }
    }

    @Test
    fun `generic update persists every field across a simulated relaunch`() {
        val expected = AppPreferences(
            themeMode = AppThemeMode.LIGHT,
            editorFontSize = 18,
            editorThemeId = "github",
            accentId = "violet",
            autoOpenLastProject = false,
            editorTabSize = 2,
            editorAutoSave = false,
            launchAfterInstall = false,
            buildOutputAab = true,
        )

        withRepository { it.update { expected } }

        val reloaded = withRepository { it.observePreferences().first() }
        assertEquals(expected, reloaded)
    }
}
