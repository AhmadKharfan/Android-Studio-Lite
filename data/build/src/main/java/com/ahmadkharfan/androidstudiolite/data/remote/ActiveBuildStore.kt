package com.ahmadkharfan.androidstudiolite.data.remote

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

data class ActiveBuild(
    val buildId: String,
    val projectId: String,
    val projectRootPath: String,
    val projectName: String,
    val installAfterSuccess: Boolean,
    val startedAtEpochMs: Long,
)

interface ActiveBuildRepository {
    fun observe(): Flow<ActiveBuild?>
    suspend fun get(): ActiveBuild?
    suspend fun save(build: ActiveBuild)
    suspend fun clear(buildId: String? = null)
}

class ActiveBuildStore(
    private val dataStore: DataStore<Preferences>,
) : ActiveBuildRepository {

    override fun observe(): Flow<ActiveBuild?> = dataStore.data.map { prefs -> prefs.toActiveBuild() }

    override suspend fun get(): ActiveBuild? = observe().first()

    override suspend fun save(build: ActiveBuild) {
        dataStore.edit { prefs ->
            prefs[BUILD_ID] = build.buildId
            prefs[PROJECT_ID] = build.projectId
            prefs[PROJECT_ROOT] = build.projectRootPath
            prefs[PROJECT_NAME] = build.projectName
            prefs[INSTALL] = build.installAfterSuccess
            prefs[STARTED_AT] = build.startedAtEpochMs
        }
    }

    override suspend fun clear(buildId: String?) {
        dataStore.edit { prefs ->
            val current = prefs[BUILD_ID]
            if (buildId == null || current == null || current == buildId) {
                prefs.remove(BUILD_ID)
                prefs.remove(PROJECT_ID)
                prefs.remove(PROJECT_ROOT)
                prefs.remove(PROJECT_NAME)
                prefs.remove(INSTALL)
                prefs.remove(STARTED_AT)
            }
        }
    }

    private fun Preferences.toActiveBuild(): ActiveBuild? {
        val buildId = this[BUILD_ID] ?: return null
        val projectId = this[PROJECT_ID] ?: return null
        val projectRoot = this[PROJECT_ROOT] ?: return null
        val projectName = this[PROJECT_NAME] ?: return null
        return ActiveBuild(
            buildId = buildId,
            projectId = projectId,
            projectRootPath = projectRoot,
            projectName = projectName,
            installAfterSuccess = this[INSTALL] ?: false,
            startedAtEpochMs = this[STARTED_AT] ?: 0L,
        )
    }

    private companion object {
        val BUILD_ID = stringPreferencesKey("active_build_id")
        val PROJECT_ID = stringPreferencesKey("active_build_project_id")
        val PROJECT_ROOT = stringPreferencesKey("active_build_project_root")
        val PROJECT_NAME = stringPreferencesKey("active_build_project_name")
        val INSTALL = booleanPreferencesKey("active_build_install")
        val STARTED_AT = longPreferencesKey("active_build_started_at")
    }
}

class InMemoryActiveBuildStore : ActiveBuildRepository {
    private val state = MutableStateFlow<ActiveBuild?>(null)
    override fun observe(): Flow<ActiveBuild?> = state
    override suspend fun get(): ActiveBuild? = state.value
    override suspend fun save(build: ActiveBuild) {
        state.value = build
    }
    override suspend fun clear(buildId: String?) {
        val current = state.value
        if (buildId == null || current == null || current.buildId == buildId) {
            state.value = null
        }
    }
}
