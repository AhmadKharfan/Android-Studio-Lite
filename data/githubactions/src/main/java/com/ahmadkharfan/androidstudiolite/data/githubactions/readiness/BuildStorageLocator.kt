package com.ahmadkharfan.androidstudiolite.data.githubactions.readiness

import android.content.Context
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.AppInstallation
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubRepository

/**
 * Remembers the id of each account's build repository. It isn't secret; it lets a renamed repository
 * still be recognised when its name no longer matches.
 */
interface BuildStorageMemory {
    fun repositoryId(login: String): Long?

    fun remember(login: String, repositoryId: Long)

    companion object {
        /** Keeps ids for the lifetime of the process only. */
        fun inMemory(): BuildStorageMemory = object : BuildStorageMemory {
            private val ids = mutableMapOf<String, Long>()
            override fun repositoryId(login: String): Long? = synchronized(ids) { ids[login.lowercase()] }
            override fun remember(login: String, repositoryId: Long) {
                synchronized(ids) { ids[login.lowercase()] = repositoryId }
            }
        }
    }
}

/** [BuildStorageMemory] in plain shared preferences on this device. */
class SharedPreferencesBuildStorageMemory(context: Context) : BuildStorageMemory {

    private val prefs by lazy { context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    override fun repositoryId(login: String): Long? =
        prefs.getLong(key(login), NO_ID).takeIf { it != NO_ID }

    override fun remember(login: String, repositoryId: Long) {
        if (repositoryId(login) != repositoryId) prefs.edit().putLong(key(login), repositoryId).apply()
    }

    private fun key(login: String) = "storage_id_${login.lowercase()}"

    private companion object {
        const val PREFS_NAME = "cloud_build_storage"
        const val NO_ID = -1L
    }
}

/**
 * Finds the build repository a GitHub App installation gives access to: by its remembered id first,
 * so a rename doesn't lose it, then by [repositoryName].
 */
internal class BuildStorageLocator(
    private val api: GitHubApiClient,
    private val repositoryName: String,
    private val memory: BuildStorageMemory,
) {

    fun rememberedId(login: String): Long? = memory.repositoryId(login)

    /** The build repository [installation] reaches, or null when it reaches none. */
    suspend fun locate(login: String, installation: AppInstallation): GitHubRepository? {
        val rememberedId = memory.repositoryId(login)
        val found = if (installation.coversAllRepositories) {
            // Every repository is visible: ask for it by name, then look for the remembered id.
            api.repository(login, repositoryName)
                ?: rememberedId?.let { id -> api.installationRepositories(installation.id).firstOrNull { it.id == id } }
        } else {
            val repositories = api.installationRepositories(installation.id)
            repositories.firstOrNull { rememberedId != null && it.id == rememberedId }
                ?: repositories.firstOrNull { it.fullName.equals("$login/$repositoryName", ignoreCase = true) }
        }
        found?.id?.let { memory.remember(login, it) }
        return found
    }

    /** The user's installation of the App, or null when it isn't installed on their account. */
    suspend fun installation(login: String): AppInstallation? =
        api.userInstallations().firstOrNull { it.account?.login.equals(login, ignoreCase = true) }

    fun remember(login: String, repository: GitHubRepository) {
        repository.id?.let { memory.remember(login, it) }
    }
}
