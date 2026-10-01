package com.ahmadkharfan.androidstudiolite.data.githubactions.readiness

import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiException
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubRepository
import com.ahmadkharfan.androidstudiolite.data.githubactions.build.BuildRepositoryProvisioner
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildStorage
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildStorageCreation
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildStorageCreator
import kotlin.coroutines.cancellation.CancellationException

/**
 * Creates the build storage with the Git features' GitHub sign-in (an OAuth token).
 *
 * The build GitHub App can't create repositories: it has neither the Administration nor the
 * "Repository creation" permission, on purpose. An OAuth token with the `repo` scope can create private
 * repositories (`POST /user/repos`), so the Git sign-in does it, but only for the same account Cloud Build
 * uses, and only as a private repository. A repository already named [repositoryName] is never changed:
 * it's used when private and reported when public.
 *
 * The App isn't granted access to a repository it didn't create, so allowing access stays a separate step.
 *
 * @param buildApi calls GitHub as Cloud Build (the App token), to learn which account builds use.
 * @param signInApi calls GitHub with the Git sign-in, which creates the repository.
 * @param hasSignIn whether the Git sign-in exists, without calling GitHub.
 */
class GitHubBuildStorageCreator(
    private val buildApi: GitHubApiClient,
    private val signInApi: GitHubApiClient,
    private val hasSignIn: () -> Boolean,
    private val repositoryName: String,
    private val memory: BuildStorageMemory,
    private val isOnline: () -> Boolean = { true },
) : CloudBuildStorageCreator {

    override val isAvailable: Boolean get() = hasSignIn()

    override suspend fun create(): CloudBuildStorageCreation {
        if (!hasSignIn()) return CloudBuildStorageCreation.NoSignIn
        if (!isOnline()) return CloudBuildStorageCreation.Offline
        return try {
            createFor(buildAccount() ?: return CloudBuildStorageCreation.BuildNotConnected)
        } catch (e: CancellationException) {
            throw e
        } catch (e: GitHubApiException) {
            outcomeFor(e)
        }
    }

    private suspend fun buildAccount(): String? = try {
        buildApi.authenticatedUser().login
    } catch (e: GitHubApiException) {
        if (e.isUnauthorized) null else throw e
    }

    private suspend fun createFor(buildAccount: String): CloudBuildStorageCreation {
        val signIn = try {
            signInApi.authenticatedUser()
        } catch (e: GitHubApiException) {
            if (e.isUnauthorized) return CloudBuildStorageCreation.SignInExpired else throw e
        }
        if (!signIn.login.equals(buildAccount, ignoreCase = true)) {
            return CloudBuildStorageCreation.AccountMismatch(signIn.login, buildAccount)
        }
        // A token that reports its scopes must have `repo` to create a private repository.
        if (signIn.scopes != null && REPO_SCOPE !in signIn.scopes) return CloudBuildStorageCreation.NotAllowed
        val created = try {
            signInApi.createUserRepository(repositoryName, BuildRepositoryProvisioner.DESCRIPTION)
        } catch (e: GitHubApiException) {
            // 422: the name is taken. Look at what's there rather than touching it.
            if (e.isValidationFailure) return existing(signIn.login) else throw e
        }
        if (!created.private) return CloudBuildStorageCreation.Failed("GitHub didn't create the build storage as private.")
        remember(signIn.login, created)
        return CloudBuildStorageCreation.Created(created.toStorage())
    }

    private suspend fun existing(login: String): CloudBuildStorageCreation {
        val repository = signInApi.repository(login, repositoryName)
            ?: return CloudBuildStorageCreation.Failed("GitHub refused to create $repositoryName and it isn't on your account.")
        if (!repository.private) return CloudBuildStorageCreation.ExistsButPublic(repository.toStorage())
        remember(login, repository)
        return CloudBuildStorageCreation.AlreadyExists(repository.toStorage())
    }

    private fun remember(login: String, repository: GitHubRepository) {
        repository.id?.let { memory.remember(login, it) }
    }

    private fun outcomeFor(error: GitHubApiException): CloudBuildStorageCreation = when {
        error.isUnauthorized -> CloudBuildStorageCreation.SignInExpired
        error.httpStatus == 0 ->
            if (isOnline()) CloudBuildStorageCreation.GitHubUnavailable else CloudBuildStorageCreation.Offline
        error.isTransient || error.isRateLimited -> CloudBuildStorageCreation.GitHubUnavailable
        error.httpStatus == HTTP_FORBIDDEN || error.isNotFound -> CloudBuildStorageCreation.NotAllowed
        else -> CloudBuildStorageCreation.Failed(error.message)
    }

    private fun GitHubRepository.toStorage() = CloudBuildStorage(id, fullName, htmlUrl, private)

    private companion object {
        const val REPO_SCOPE = "repo"
        const val HTTP_FORBIDDEN = 403
    }
}
