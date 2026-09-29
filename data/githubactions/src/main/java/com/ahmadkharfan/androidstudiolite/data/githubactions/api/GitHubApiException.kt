package com.ahmadkharfan.androidstudiolite.data.githubactions.api

/**
 * A failed GitHub API call. [httpStatus] is 0 for transport failures (no response).
 *
 * @param retryAfterSeconds when GitHub asked to wait before retrying (secondary rate limit), or null.
 * @param rateLimitResetEpochSeconds when the exhausted primary rate limit resets, or null.
 */
class GitHubApiException(
    val httpStatus: Int,
    override val message: String,
    val retryAfterSeconds: Long? = null,
    val rateLimitResetEpochSeconds: Long? = null,
    cause: Throwable? = null,
) : Exception(message, cause) {

    val isUnauthorized: Boolean get() = httpStatus == 401
    val isNotFound: Boolean get() = httpStatus == 404
    val isValidationFailure: Boolean get() = httpStatus == 422
    val isRateLimited: Boolean get() = retryAfterSeconds != null || rateLimitResetEpochSeconds != null
    val isTransient: Boolean get() = httpStatus == 0 || httpStatus in 500..599
}
