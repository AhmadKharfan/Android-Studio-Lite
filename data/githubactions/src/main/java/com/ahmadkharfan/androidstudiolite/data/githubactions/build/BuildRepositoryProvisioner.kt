package com.ahmadkharfan.androidstudiolite.data.githubactions.build

import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiClient
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiException
import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubRepository
import com.ahmadkharfan.androidstudiolite.data.githubactions.workflow.BuildWorkflow

/** The user's build repository, ready to receive snapshots and dispatches. */
internal data class BuildRepository(
    val owner: String,
    val name: String,
    val defaultBranch: String,
    val cloneUrl: String,
)

/**
 * Makes sure the signed-in user has a private build repository with the current build workflow.
 *
 * The repository is created on first use. The workflow is (re)written whenever it differs from the
 * one this app ships, unless the repository already holds a newer version written by a newer app.
 * A public repository is refused: every build pushes the project's source code there.
 */
internal class BuildRepositoryProvisioner(
    private val api: GitHubApiClient,
    private val repositoryName: String,
    private val gitBaseUrl: String,
    private val waitBeforeRetry: suspend (Long) -> Unit,
) {

    @Volatile private var provisioned: Pair<String, BuildRepository>? = null

    suspend fun ensure(): BuildRepository {
        val user = api.authenticatedUser()
        if (user.scopes != null && !user.scopes.containsAll(REQUIRED_SCOPES)) {
            throw BuildFailure(GitHubBuildMessages.WORKFLOW_SCOPE_MISSING)
        }
        provisioned?.takeIf { it.first == user.login }?.let { return it.second }
        val repository = api.repository(user.login, repositoryName)
            ?: api.createUserRepository(repositoryName, DESCRIPTION)
        if (!repository.private) {
            throw BuildFailure(
                "github.com/${repository.fullName} is public, and builds would publish your source code there. " +
                    "Make it private or delete it, then build again.",
            )
        }
        return target(repository).also {
            ensureWorkflow(it)
            provisioned = user.login to it
        }
    }

    private fun target(repository: GitHubRepository) = BuildRepository(
        owner = repository.fullName.substringBefore('/'),
        name = repository.fullName.substringAfter('/'),
        defaultBranch = repository.defaultBranch ?: DEFAULT_BRANCH,
        cloneUrl = "${gitBaseUrl.trimEnd('/')}/${repository.fullName}.git",
    )

    private suspend fun ensureWorkflow(repository: BuildRepository) {
        val wanted = BuildWorkflow.contents()
        val existing = api.file(repository.owner, repository.name, BuildWorkflow.PATH, repository.defaultBranch)
        if (existing?.content == wanted) return
        val existingVersion = existing?.let { BuildWorkflow.versionOf(it.content) } ?: 0
        if (existingVersion > BuildWorkflow.VERSION) return
        retryWhileInitialising {
            api.putFile(
                owner = repository.owner,
                repo = repository.name,
                path = BuildWorkflow.PATH,
                content = wanted,
                message = if (existing == null) "Add the Android Studio Lite build workflow" else "Update the Android Studio Lite build workflow",
                sha = existing?.sha,
                branch = repository.defaultBranch,
            )
        }
    }

    /** A just-created repository can briefly reject writes while GitHub initialises it. */
    private suspend fun retryWhileInitialising(write: suspend () -> Unit) {
        var attempt = 0
        while (true) {
            val failure = runCatching { write() }.exceptionOrNull() ?: return
            val initialising = failure is GitHubApiException && (failure.isNotFound || failure.httpStatus == HTTP_CONFLICT)
            if (!initialising || attempt >= INITIALISING_ATTEMPTS - 1) throw failure
            waitBeforeRetry(INITIALISING_BACKOFF_MS)
            attempt++
        }
    }

    private companion object {
        val REQUIRED_SCOPES = setOf("repo", "workflow")
        const val DESCRIPTION = "Builds for Android Studio Lite. Managed by the app; keep it private."
        const val DEFAULT_BRANCH = "main"
        const val HTTP_CONFLICT = 409
        const val INITIALISING_ATTEMPTS = 5
        const val INITIALISING_BACKOFF_MS = 2_000L
    }
}
