package com.ahmadkharfan.androidstudiolite.data.githubactions.api

/**
 * A failed GitHub API call. [httpStatus] is 0 for transport failures (no response).
 *
 * @param retryAfterSeconds when GitHub asked to wait before retrying (secondary rate limit), or null.
 * @param rateLimitResetEpochSeconds when the exhausted primary rate limit resets, or null.
 * @param acceptedPermissions GitHub's `X-Accepted-GitHub-Permissions`: the App permissions the call needs.
 */
class GitHubApiException(
    val httpStatus: Int,
    override val message: String,
    val retryAfterSeconds: Long? = null,
    val rateLimitResetEpochSeconds: Long? = null,
    cause: Throwable? = null,
    val acceptedPermissions: String? = null,
) : Exception(message, cause) {

    val isUnauthorized: Boolean get() = httpStatus == 401
    val isNotFound: Boolean get() = httpStatus == 404
    val isValidationFailure: Boolean get() = httpStatus == 422
    val isRateLimited: Boolean get() = retryAfterSeconds != null || rateLimitResetEpochSeconds != null
    val isTransient: Boolean get() = httpStatus == 0 || httpStatus in 500..599

    /** A 403 that names the permissions the call needs: the App's access is short of them. */
    val isMissingPermission: Boolean get() = httpStatus == 403 && !isRateLimited && !acceptedPermissions.isNullOrBlank()
}
