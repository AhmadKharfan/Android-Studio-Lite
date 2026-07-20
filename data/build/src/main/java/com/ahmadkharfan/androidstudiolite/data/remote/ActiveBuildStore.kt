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
    val operationId: String = buildId,
    val projectId: String,
    val projectRootPath: String,
    val projectName: String,
    val installAfterSuccess: Boolean,
    val autoLaunchAfterInstall: Boolean = true,
    val startedAtEpochMs: Long,
    val modulePath: String = ":app",
    val variantName: String = "debug",
    val kind: String = "ASSEMBLE",
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
            prefs[OPERATION_ID] = build.operationId
            prefs[PROJECT_ID] = build.projectId
            prefs[PROJECT_ROOT] = build.projectRootPath
            prefs[PROJECT_NAME] = build.projectName
            prefs[INSTALL] = build.installAfterSuccess
            prefs[AUTO_LAUNCH] = build.autoLaunchAfterInstall
            prefs[STARTED_AT] = build.startedAtEpochMs
            prefs[MODULE_PATH] = build.modulePath
            prefs[VARIANT] = build.variantName
            prefs[KIND] = build.kind
        }
    }

    override suspend fun clear(buildId: String?) {
        dataStore.edit { prefs ->
            val current = prefs[BUILD_ID]
            if (buildId == null || current == null || current == buildId) {
                prefs.remove(BUILD_ID)
                prefs.remove(OPERATION_ID)
                prefs.remove(PROJECT_ID)
                prefs.remove(PROJECT_ROOT)
                prefs.remove(PROJECT_NAME)
                prefs.remove(INSTALL)
                prefs.remove(AUTO_LAUNCH)
                prefs.remove(STARTED_AT)
                prefs.remove(MODULE_PATH)
                prefs.remove(VARIANT)
                prefs.remove(KIND)
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
            operationId = this[OPERATION_ID] ?: buildId,
            projectId = projectId,
            projectRootPath = projectRoot,
            projectName = projectName,
            installAfterSuccess = this[INSTALL] ?: false,
            autoLaunchAfterInstall = this[AUTO_LAUNCH] ?: true,
            startedAtEpochMs = this[STARTED_AT] ?: 0L,
            modulePath = this[MODULE_PATH] ?: ":app",
            variantName = this[VARIANT] ?: "debug",
            kind = this[KIND] ?: "ASSEMBLE",
        )
    }

    private companion object {
        val BUILD_ID = stringPreferencesKey("active_build_id")
        val OPERATION_ID = stringPreferencesKey("active_build_operation_id")
        val PROJECT_ID = stringPreferencesKey("active_build_project_id")
        val PROJECT_ROOT = stringPreferencesKey("active_build_project_root")
        val PROJECT_NAME = stringPreferencesKey("active_build_project_name")
        val INSTALL = booleanPreferencesKey("active_build_install")
        val AUTO_LAUNCH = booleanPreferencesKey("active_build_auto_launch")
        val STARTED_AT = longPreferencesKey("active_build_started_at")
        val MODULE_PATH = stringPreferencesKey("active_build_module_path")
        val VARIANT = stringPreferencesKey("active_build_variant")
        val KIND = stringPreferencesKey("active_build_kind")
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
