package com.ahmadkharfan.androidstudiolite.data.githubactions.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The signed-in user, plus the OAuth scopes the token carries (null when GitHub doesn't report them). */
data class AuthenticatedUser(val login: String, val scopes: Set<String>?)

@Serializable
internal data class UserDto(val login: String)

@Serializable
data class GitHubRepository(
    @SerialName("full_name") val fullName: String,
    val private: Boolean,
    @SerialName("default_branch") val defaultBranch: String? = null,
    @SerialName("clone_url") val cloneUrl: String? = null,
    val id: Long? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
)

/** An installation of this GitHub App. [htmlUrl] is where its access is managed on GitHub. */
@Serializable
data class AppInstallation(
    val id: Long,
    val account: InstallationAccount? = null,
    @SerialName("repository_selection") val repositorySelection: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("suspended_at") val suspendedAt: String? = null,
) {
    /** True only when GitHub says the App can see every repository of the account. */
    val coversAllRepositories: Boolean get() = repositorySelection == "all"
}

@Serializable
data class InstallationAccount(val login: String? = null)

@Serializable
internal data class InstallationsPage(val installations: List<AppInstallation> = emptyList())

@Serializable
internal data class InstallationRepositoriesPage(val repositories: List<GitHubRepository> = emptyList())

@Serializable
internal data class CreateRepositoryRequest(
    val name: String,
    val description: String,
    val private: Boolean,
    @SerialName("auto_init") val autoInit: Boolean,
)

/** A file read through the contents API. [sha] is the blob sha needed to update it. */
data class RepositoryFile(val path: String, val sha: String, val content: String)

@Serializable
internal data class ContentDto(
    val path: String,
    val sha: String,
    val content: String? = null,
    val encoding: String? = null,
)

@Serializable
internal data class PutContentRequest(
    val message: String,
    val content: String,
    val sha: String? = null,
    val branch: String? = null,
)

@Serializable
internal data class DispatchRequest(
    val ref: String,
    val inputs: Map<String, String>,
    /** Asks GitHub to answer with the new run's id rather than an empty 204. */
    @SerialName("return_run_details") val returnRunDetails: Boolean,
)

@Serializable
internal data class DispatchResponse(@SerialName("workflow_run_id") val workflowRunId: Long? = null)

@Serializable
data class WorkflowRun(
    val id: Long,
    val status: String? = null,
    val conclusion: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("run_started_at") val runStartedAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    val isCompleted: Boolean get() = status == "completed"
}

@Serializable
internal data class WorkflowRunsPage(@SerialName("workflow_runs") val workflowRuns: List<WorkflowRun> = emptyList())

@Serializable
data class WorkflowJob(
    val id: Long,
    val name: String,
    val status: String? = null,
    val conclusion: String? = null,
    val steps: List<WorkflowStep> = emptyList(),
    @SerialName("check_run_url") val checkRunUrl: String? = null,
) {
    /** The job's check run, whose annotations hold GitHub's own messages about it. */
    val checkRunId: Long? get() = checkRunUrl?.substringAfterLast('/')?.toLongOrNull()
}

/** A message GitHub attached to a job, e.g. why it never started or was cancelled. */
@Serializable
data class CheckAnnotation(
    @SerialName("annotation_level") val level: String? = null,
    val message: String? = null,
)

@Serializable
data class WorkflowStep(
    val name: String,
    val number: Int,
    val status: String? = null,
    val conclusion: String? = null,
)

@Serializable
internal data class WorkflowJobsPage(val jobs: List<WorkflowJob> = emptyList())

@Serializable
data class WorkflowArtifact(
    val id: Long,
    val name: String,
    @SerialName("size_in_bytes") val sizeInBytes: Long = 0,
    val expired: Boolean = false,
)

@Serializable
internal data class ArtifactsPage(val artifacts: List<WorkflowArtifact> = emptyList())

@Serializable
internal data class ErrorDto(
    val message: String? = null,
    @SerialName("documentation_url") val documentationUrl: String? = null,
)

/** The primary rate limit as last reported by GitHub. */
data class RateLimit(val limit: Int, val remaining: Int, val resetEpochSeconds: Long)

@Serializable
data class CheckRun(
    val id: Long,
    val name: String,
    val status: String? = null,
    val output: CheckRunOutput? = null,
)

@Serializable
data class CheckRunOutput(
    val title: String? = null,
    val summary: String? = null,
    val text: String? = null,
)

@Serializable
internal data class CheckRunsPage(@SerialName("check_runs") val checkRuns: List<CheckRun> = emptyList())
