package com.ahmadkharfan.androidstudiolite.data.githubactions.auth

import com.ahmadkharfan.androidstudiolite.domain.model.GitHubTokenGrant
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Renews a GitHub App user token with its refresh token.
 *
 * Tokens obtained through device flow refresh with just the client id; no client secret is involved.
 */
class GitHubTokenRefresher(
    private val clientId: String,
    private val httpClient: OkHttpClient = defaultClient(),
    private val baseUrl: HttpUrl = "https://github.com/".toHttpUrl(),
) {
    sealed interface Result {
        data class Renewed(val grant: GitHubTokenGrant) : Result
        data object Rejected : Result
        data object Unavailable : Result
    }

    suspend fun refresh(refreshToken: String): Result = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl.newBuilder().addPathSegments("login/oauth/access_token").build())
            .header("Accept", "application/json")
            .post(
                FormBody.Builder()
                    .add("client_id", clientId)
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .build(),
            )
            .build()
        val outcome = runCatching {
            httpClient.newCall(request).execute().use { response ->
                val body = response.body.string()
                when {
                    response.code >= SERVER_ERROR -> Result.Unavailable
                    else -> interpret(runCatching { JSON.decodeFromString<TokenResponse>(body) }.getOrNull())
                }
            }
        }
        // A network failure isn't a verdict on the refresh token: report it as unavailable, not rejected.
        outcome.exceptionOrNull()?.takeUnless { it is IOException }?.let { throw it }
        outcome.getOrDefault(Result.Unavailable)
    }

    // GitHub answers refresh problems with HTTP 200 and an `error` field.
    private fun interpret(response: TokenResponse?): Result {
        val token = response?.accessToken?.takeIf { it.isNotBlank() }
            ?: return if (response?.error != null) Result.Rejected else Result.Unavailable
        return Result.Renewed(
            GitHubTokenGrant(
                accessToken = token,
                expiresInSeconds = response.expiresIn,
                refreshToken = response.refreshToken?.takeIf { it.isNotBlank() },
                refreshTokenExpiresInSeconds = response.refreshTokenExpiresIn,
            ),
        )
    }

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String? = null,
        @SerialName("expires_in") val expiresIn: Long? = null,
        @SerialName("refresh_token") val refreshToken: String? = null,
        @SerialName("refresh_token_expires_in") val refreshTokenExpiresIn: Long? = null,
        val error: String? = null,
    )

    companion object {
        private const val SERVER_ERROR = 500
        private val JSON = Json { ignoreUnknownKeys = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
