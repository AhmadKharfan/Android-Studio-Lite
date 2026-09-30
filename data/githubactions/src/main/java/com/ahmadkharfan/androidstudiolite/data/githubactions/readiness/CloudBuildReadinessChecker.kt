package com.ahmadkharfan.androidstudiolite.data.githubactions.readiness

import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiException
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubRepository
import com.ahmadkharfan.androidstudiolite.data.githubactions.build.BuildRepositoryProvisioner
import com.ahmadkharfan.androidstudiolite.data.githubactions.build.RepositorySetup
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildState
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildStorage
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.serialization.SerializationException

/**
 * Asks GitHub whether Cloud Build can build now, in order: connection, account, App installation,
 * build repository, its visibility. The first thing missing decides the state.
 *
 * With [RepositorySetup.Manual] builds run on a GitHub App token, which is what can read the App's
 * installations. With [RepositorySetup.CreateIfMissing] builds use an OAuth token that can't, and the
 * repository is created on first build, so only the connection and its scopes are checked.
 *
 */
class CloudBuildReadinessChecker(
    private val api: GitHubApiClient,
    private val token: suspend () -> String?,
    repositoryName: String,
    private val setup: RepositorySetup,
    memory: BuildStorageMemory,
    private val signals: ReadinessSignals = ReadinessSignals(),
) {

    private val locator = BuildStorageLocator(api, repositoryName, memory)

    suspend fun check(): CloudBuildState {
        if (!signals.isOnline()) return CloudBuildState.Offline
        if (token().isNullOrBlank()) {
            return if (signals.signedOutByGitHub()) CloudBuildState.Revoked else CloudBuildState.NotConnected
        }
        return try {
            checkAccount()
        } catch (e: CancellationException) {
            throw e
        } catch (e: GitHubApiException) {
            stateFor(e, manageUrl = null)
        } catch (e: SerializationException) {
            CloudBuildState.Unknown("GitHub sent an answer the app couldn't read: ${e.message}")
        }
    }

    private suspend fun checkAccount(): CloudBuildState {
        val user = api.authenticatedUser()
        return when (setup) {
            RepositorySetup.CreateIfMissing ->
                if (user.scopes?.containsAll(BuildRepositoryProvisioner.REQUIRED_SCOPES) == false) {
                    // An OAuth token without `workflow` can't install the build workflow: a new sign-in fixes it.
                    CloudBuildState.PermissionUpdateRequired(manageUrl = null, acceptedPermissions = null)
                } else {
                    CloudBuildState.Ready(user.login, storage = null)
                }
            is RepositorySetup.Manual -> checkInstallation(user.login, setup)
        }
    }

    private suspend fun checkInstallation(login: String, setup: RepositorySetup.Manual): CloudBuildState {
        val installation = locator.installation(login) ?: return CloudBuildState.AccessMissing(setup.installUrl)
        val manageUrl = installation.htmlUrl
        if (installation.suspendedAt != null) return CloudBuildState.AccessPaused(manageUrl)
        val wasReachableBefore = locator.rememberedId(login) != null
        val repository = try {
            locator.locate(login, installation)
        } catch (e: GitHubApiException) {
            return stateFor(e, manageUrl)
        } ?: return if (installation.coversAllRepositories) {
            CloudBuildState.StorageMissing(manageUrl)
        } else {
            CloudBuildState.StorageNotReachable(manageUrl, wasReachableBefore)
        }
        val storage = repository.toStorage()
        return if (repository.private) CloudBuildState.Ready(login, storage) else CloudBuildState.StoragePublic(storage)
    }

    /** What a failed call says about readiness; anything unrecognised stays [CloudBuildState.Unknown]. */
    private fun stateFor(error: GitHubApiException, manageUrl: String?): CloudBuildState = when {
        error.isRateLimited -> CloudBuildState.RateLimited(
            error.rateLimitResetEpochSeconds ?: error.retryAfterSeconds?.let { signals.nowEpochSeconds() + it },
        )
        error.isUnauthorized -> CloudBuildState.Revoked
        error.isMissingPermission -> CloudBuildState.PermissionUpdateRequired(manageUrl, error.acceptedPermissions)
        error.httpStatus == 0 -> if (signals.isOnline()) CloudBuildState.GitHubUnavailable else CloudBuildState.Offline
        error.isTransient -> CloudBuildState.GitHubUnavailable
        else -> CloudBuildState.Unknown(error.message)
    }

    private fun GitHubRepository.toStorage() = CloudBuildStorage(id, fullName, htmlUrl, private)
}

/**
 * What the checker needs to know besides GitHub's answers.
 *
 * @param signedOutByGitHub true when GitHub itself ended the connection (its refresh was rejected), so
 *   a missing token means "revoked" rather than "never connected".
 */
class ReadinessSignals(
    val isOnline: () -> Boolean = { true },
    val signedOutByGitHub: () -> Boolean = { false },
    val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / MILLIS_PER_SECOND },
) {
    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
