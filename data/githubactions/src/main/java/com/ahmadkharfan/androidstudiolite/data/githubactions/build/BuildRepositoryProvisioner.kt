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
 *
 * With [setup] set to [RepositorySetup.Manual] (a GitHub App, which has no permission to create
 * repositories), a missing repository is reported with the steps to create it instead.
 */
internal class BuildRepositoryProvisioner(
    private val api: GitHubApiClient,
    private val repositoryName: String,
    private val gitBaseUrl: String,
    private val setup: RepositorySetup,
    private val waitBeforeRetry: suspend (Long) -> Unit,
) {

    @Volatile private var provisioned: Pair<String, BuildRepository>? = null

    suspend fun ensure(): BuildRepository {
        val user = api.authenticatedUser()
        if (user.scopes != null && !user.scopes.containsAll(REQUIRED_SCOPES)) {
            throw BuildFailure(GitHubBuildMessages.WORKFLOW_SCOPE_MISSING)
        }
        provisioned?.takeIf { it.first == user.login }?.let { return it.second }
        val repository = api.repository(user.login, repositoryName) ?: missingRepository()
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

    private suspend fun missingRepository(): GitHubRepository = when (setup) {
        RepositorySetup.CreateIfMissing -> api.createUserRepository(repositoryName, DESCRIPTION)
        is RepositorySetup.Manual -> throw BuildFailure(setup.message(repositoryName))
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

/** Who makes the build repository: the app, or the user (when the app may not create repositories). */
sealed interface RepositorySetup {
    data object CreateIfMissing : RepositorySetup

    /** @param installUrl where to install the GitHub App, or null to point at the user's installations. */
    data class Manual(val installUrl: String?) : RepositorySetup {
        fun message(repositoryName: String): String =
            "Builds need a private repository named $repositoryName on your GitHub account, with the build GitHub App " +
                "installed on that repository only. Create the repository, then install the App: " +
                (installUrl ?: "https://github.com/settings/installations")
    }
}
