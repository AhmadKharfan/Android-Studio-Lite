package com.ahmadkharfan.androidstudiolite.data.githubactions.build

import com.ahmadkharfan.androidstudiolite.data.githubactions.api.GitHubApiException
import com.ahmadkharfan.androidstudiolite.data.githubactions.snapshot.SnapshotException
import java.text.DateFormat
import java.util.Date

/** User-facing text for GitHub Actions build failures. */
internal object GitHubBuildMessages {

    const val SIGN_IN_REQUIRED = "Sign in to GitHub in Settings to build with GitHub Actions."
    const val WORKFLOW_SCOPE_MISSING =
        "Sign in to GitHub again and allow workflow access, so the app can set up its build workflow."

    fun forError(error: Throwable, formatTime: (Long) -> String = ::localTime): String = when (error) {
        is BuildFailure -> error.message
        is SnapshotException -> error.message ?: "Couldn't upload the project to GitHub."
        is GitHubApiException -> forApiError(error, formatTime)
        is IllegalArgumentException -> "This build can't run on GitHub Actions: ${error.message}"
        else -> error.message?.takeIf { it.isNotBlank() } ?: "The GitHub Actions build failed."
    }

    private fun forApiError(error: GitHubApiException, formatTime: (Long) -> String): String = when {
        error.isUnauthorized -> "Your GitHub sign-in has expired or was revoked. $SIGN_IN_REQUIRED"
        error.rateLimitResetEpochSeconds != null ->
            "GitHub's API limit for your account is used up until ${formatTime(error.rateLimitResetEpochSeconds * 1000)}. " +
                "Try again then."
        error.retryAfterSeconds != null ->
            "GitHub is limiting requests right now. Try again in ${error.retryAfterSeconds} seconds."
        error.httpStatus == 0 -> "Can't reach GitHub. Check your internet connection and try again."
        error.httpStatus in 500..599 -> "GitHub is having problems right now (HTTP ${error.httpStatus}). Try again later."
        else -> "GitHub refused the request: ${error.message}"
    }

    /** The message for a run that finished without success, or null when it succeeded. */
    fun forConclusion(conclusion: String?, runUrl: String?): String? {
        val seeRun = runUrl?.let { " See the run on GitHub: $it" }.orEmpty()
        return when (conclusion) {
            "success" -> null
            "failure" -> "The build failed.$seeRun"
            "cancelled" -> "The build was cancelled on GitHub."
            "timed_out" -> "The build ran longer than GitHub's time limit for it and was stopped."
            "startup_failure" -> "GitHub couldn't start the build workflow.$seeRun"
            "action_required" -> "GitHub needs you to approve this run before it starts.$seeRun"
            "stale" -> "GitHub dropped the build before it ran. Try again."
            else -> "The build ended on GitHub with status ${conclusion ?: "unknown"}.$seeRun"
        }
    }

    private fun localTime(epochMillis: Long): String =
        DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(epochMillis))
}

/** A build failure whose [message] is already meant for the user. */
internal class BuildFailure(override val message: String) : Exception(message)
