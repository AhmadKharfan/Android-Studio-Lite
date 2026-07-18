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

/**
 * Verifies that [DataStorePreferencesRepository] round-trips through a real file-backed DataStore:
 * a fresh repository instance pointed at the same file reads back what a previous instance wrote,
 * which is exactly what "settings survive process death" means.
 */
class DataStorePreferencesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var file: File

    @Before
    fun setUp() {
        file = tmp.newFile("app_preferences.preferences_pb").also { it.delete() }
    }

    /**
     * Opens a fresh DataStore + repository over [file] in its own coroutine scope, runs [block],
     * then cancels the scope so the store is fully released. Each call simulates a distinct process
     * lifetime — DataStore forbids two live instances over the same file, so the scope must be torn
     * down before the next "launch" opens the file again.
     */
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
            repo.setLanguage("ar")
            repo.setAutoOpenLastProject(false)
            repo.setSnowfallEasterEgg(true)
        }

        val reloaded = withRepository { it.observePreferences().first() }

        assertEquals(AppThemeMode.DARK, reloaded.themeMode)
        assertEquals(21, reloaded.editorFontSize)
        assertEquals("solarized", reloaded.editorThemeId)
        assertEquals("crimson", reloaded.accentId)
        assertEquals("ar", reloaded.language)
        assertEquals(false, reloaded.autoOpenLastProject)
        assertEquals(true, reloaded.snowfallEasterEgg)
    }

    @Test
    fun `generic update persists every field across a simulated relaunch`() {
        val expected = AppPreferences(
            themeMode = AppThemeMode.LIGHT,
            editorFontSize = 18,
            editorThemeId = "github",
            accentId = "violet",
            language = "fr",
            autoOpenLastProject = false,
            snowfallEasterEgg = true,
            editorTabSize = 2,
            editorAutoSave = false,
            launchAfterInstall = false,
            installViaShizuku = true,
        )

        withRepository { it.update { expected } }

        val reloaded = withRepository { it.observePreferences().first() }
        assertEquals(expected, reloaded)
    }
}
