package com.ahmadkharfan.androidstudiolite.data.local

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.ahmadkharfan.androidstudiolite.domain.model.AppPreferences
import com.ahmadkharfan.androidstudiolite.domain.model.AppThemeMode
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Real [PreferencesRepository] backed by DataStore, mirroring the DataStore usage in
 * [com.ahmadkharfan.androidstudiolite.data.onboarding.AndroidOnboardingRepository]. Every field on
 * [AppPreferences] is persisted so settings survive process death. Missing keys fall back to the
 * [AppPreferences] defaults, so first launch (and any newly-added field) reads the default value.
 *
 * Takes a [DataStore] rather than a [android.content.Context] so it can be exercised in plain JVM
 * unit tests with a temp-file-backed store (see the Koin `preferencesModule` for production wiring).
 */
class DataStorePreferencesRepository(
    private val dataStore: DataStore<Preferences>,
) : PreferencesRepository {

    override fun observePreferences(): Flow<AppPreferences> =
        dataStore.data.map { it.toAppPreferences() }

    override suspend fun setThemeMode(mode: AppThemeMode) =
        update { it.copy(themeMode = mode) }

    override suspend fun setEditorFontSize(size: Int) =
        update { it.copy(editorFontSize = size) }

    override suspend fun setEditorTheme(id: String) =
        update { it.copy(editorThemeId = id) }

    override suspend fun setShareUsageStats(enabled: Boolean) =
        update { it.copy(shareUsageStats = enabled) }

    override suspend fun setAccent(id: String) =
        update { it.copy(accentId = id) }

    override suspend fun setLanguage(language: String) =
        update { it.copy(language = language) }

    override suspend fun setAutoOpenLastProject(enabled: Boolean) =
        update { it.copy(autoOpenLastProject = enabled) }

    override suspend fun setSnowfallEasterEgg(enabled: Boolean) =
        update { it.copy(snowfallEasterEgg = enabled) }

    override suspend fun update(transform: (AppPreferences) -> AppPreferences) {
        dataStore.edit { prefs ->
            prefs.writeAppPreferences(transform(prefs.toAppPreferences()))
        }
    }

    /** Reads an [AppPreferences] from DataStore, defaulting any missing key to its [AppPreferences] default. */
    private fun Preferences.toAppPreferences(): AppPreferences {
        val defaults = AppPreferences()
        return AppPreferences(
            themeMode = this[THEME_MODE]?.let { runCatching { AppThemeMode.valueOf(it) }.getOrNull() }
                ?: defaults.themeMode,
            editorFontSize = this[EDITOR_FONT_SIZE] ?: defaults.editorFontSize,
            editorThemeId = this[EDITOR_THEME_ID] ?: defaults.editorThemeId,
            shareUsageStats = this[SHARE_USAGE_STATS] ?: defaults.shareUsageStats,
            accentId = this[ACCENT_ID] ?: defaults.accentId,
            language = this[LANGUAGE] ?: defaults.language,
            autoOpenLastProject = this[AUTO_OPEN_LAST_PROJECT] ?: defaults.autoOpenLastProject,
            snowfallEasterEgg = this[SNOWFALL_EASTER_EGG] ?: defaults.snowfallEasterEgg,
            editorTabSize = this[EDITOR_TAB_SIZE] ?: defaults.editorTabSize,
            editorAutoSave = this[EDITOR_AUTO_SAVE] ?: defaults.editorAutoSave,
            launchAfterInstall = this[LAUNCH_AFTER_INSTALL] ?: defaults.launchAfterInstall,
            installViaShizuku = this[INSTALL_VIA_SHIZUKU] ?: defaults.installViaShizuku,
            buildOutputAab = this[BUILD_OUTPUT_AAB] ?: defaults.buildOutputAab,
            preferGitSource = this[PREFER_GIT_SOURCE] ?: defaults.preferGitSource,
        )
    }

    /** Writes every [AppPreferences] field so the persisted state fully matches [value]. */
    private fun MutablePreferences.writeAppPreferences(value: AppPreferences) {
        this[THEME_MODE] = value.themeMode.name
        this[EDITOR_FONT_SIZE] = value.editorFontSize
        this[EDITOR_THEME_ID] = value.editorThemeId
        this[SHARE_USAGE_STATS] = value.shareUsageStats
        this[ACCENT_ID] = value.accentId
        this[LANGUAGE] = value.language
        this[AUTO_OPEN_LAST_PROJECT] = value.autoOpenLastProject
        this[SNOWFALL_EASTER_EGG] = value.snowfallEasterEgg
        this[EDITOR_TAB_SIZE] = value.editorTabSize
        this[EDITOR_AUTO_SAVE] = value.editorAutoSave
        this[LAUNCH_AFTER_INSTALL] = value.launchAfterInstall
        this[INSTALL_VIA_SHIZUKU] = value.installViaShizuku
        this[BUILD_OUTPUT_AAB] = value.buildOutputAab
        this[PREFER_GIT_SOURCE] = value.preferGitSource
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val EDITOR_FONT_SIZE = intPreferencesKey("editor_font_size")
        val EDITOR_THEME_ID = stringPreferencesKey("editor_theme_id")
        val SHARE_USAGE_STATS = booleanPreferencesKey("share_usage_stats")
        val ACCENT_ID = stringPreferencesKey("accent_id")
        val LANGUAGE = stringPreferencesKey("language")
        val AUTO_OPEN_LAST_PROJECT = booleanPreferencesKey("auto_open_last_project")
        val SNOWFALL_EASTER_EGG = booleanPreferencesKey("snowfall_easter_egg")
        val EDITOR_TAB_SIZE = intPreferencesKey("editor_tab_size")
        val EDITOR_AUTO_SAVE = booleanPreferencesKey("editor_auto_save")
        val LAUNCH_AFTER_INSTALL = booleanPreferencesKey("launch_after_install")
        val INSTALL_VIA_SHIZUKU = booleanPreferencesKey("install_via_shizuku")
        val BUILD_OUTPUT_AAB = booleanPreferencesKey("build_output_aab")
        val PREFER_GIT_SOURCE = booleanPreferencesKey("prefer_git_source")
    }
}
