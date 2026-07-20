package com.ahmadkharfan.androidstudiolite.data.remote.github

import com.ahmadkharfan.androidstudiolite.domain.model.GitCredentials
import com.ahmadkharfan.androidstudiolite.domain.repository.GitCredentialStore
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthState
import com.ahmadkharfan.androidstudiolite.domain.repository.GitHubDeviceAuthenticator
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

class GitHubDeviceFlowAuthenticator(
    private val clientId: String,
    private val credentialStore: GitCredentialStore,
    private val httpClient: OkHttpClient = defaultClient(),
    private val scope: String = "repo",
) : GitHubDeviceAuthenticator {

    override val isConfigured: Boolean get() = clientId.isNotBlank()

    override fun authenticate(): Flow<GitHubDeviceAuthState> = flow {
        if (!isConfigured) {
            emit(GitHubDeviceAuthState.Error("GitHub sign-in isn't configured. Use an access token instead."))
            return@flow
        }
        emit(GitHubDeviceAuthState.RequestingCode)

        val code = try {
            requestDeviceCode()
        } catch (e: IOException) {
            emit(GitHubDeviceAuthState.Error(e.message ?: "Couldn't reach GitHub"))
            return@flow
        }
        emit(GitHubDeviceAuthState.AwaitingAuthorization(code.userCode, code.verificationUri))

        var intervalSeconds = code.interval.coerceAtLeast(MIN_POLL_SECONDS)
        val deadline = System.currentTimeMillis() + code.expiresIn * 1000L


        var consecutiveErrors = 0
        while (System.currentTimeMillis() < deadline) {
            delay(intervalSeconds * 1000L)
            val token = try {
                pollForToken(code.deviceCode).also { consecutiveErrors = 0 }
            } catch (e: IOException) {
                consecutiveErrors++
                if (consecutiveErrors >= MAX_CONSECUTIVE_POLL_ERRORS) {
                    emit(GitHubDeviceAuthState.Error(e.message ?: "Couldn't reach GitHub. Check your connection."))
                    return@flow
                }
                continue
            }
            when (token) {
                is TokenPoll.Pending -> Unit
                is TokenPoll.SlowDown -> intervalSeconds += SLOW_DOWN_STEP_SECONDS
                is TokenPoll.Denied -> {
                    emit(GitHubDeviceAuthState.Error(token.message))
                    return@flow
                }
                is TokenPoll.Granted -> {
                    credentialStore.save(
                        GITHUB_HOST,
                        GitCredentials(username = DEFAULT_USERNAME, token = token.accessToken),
                    )
                    val login = runCatching { fetchLogin(token.accessToken) }.getOrNull()
                    emit(GitHubDeviceAuthState.Success(login))
                    return@flow
                }
            }
        }
        emit(GitHubDeviceAuthState.Error("The code expired before sign-in completed. Try again."))
    }.flowOn(Dispatchers.IO)

    private fun requestDeviceCode(): DeviceCode {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("scope", scope)
            .build()
        val request = Request.Builder()
            .url("https://github.com/login/device/code")
            .header("Accept", "application/json")
            .post(body)
            .build()
        val response = executeToJson<DeviceCodeResponse>(request)
        return DeviceCode(
            deviceCode = response.deviceCode ?: throw IOException("GitHub didn't return a device code"),
            userCode = response.userCode.orEmpty(),
            verificationUri = response.verificationUri ?: "https://github.com/login/device",
            interval = response.interval ?: MIN_POLL_SECONDS,
            expiresIn = response.expiresIn ?: DEFAULT_EXPIRY_SECONDS,
        )
    }

    private fun pollForToken(deviceCode: String): TokenPoll {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("device_code", deviceCode)
            .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
            .build()
        val request = Request.Builder()
            .url("https://github.com/login/oauth/access_token")
            .header("Accept", "application/json")
            .post(body)
            .build()
        val response = executeToJson<AccessTokenResponse>(request)
        response.accessToken?.takeIf { it.isNotBlank() }?.let { return TokenPoll.Granted(it) }
        return when (response.error) {
            "authorization_pending" -> TokenPoll.Pending
            "slow_down" -> TokenPoll.SlowDown
            "access_denied" -> TokenPoll.Denied("Sign-in was cancelled on GitHub.")
            "expired_token" -> TokenPoll.Denied("The code expired before sign-in completed. Try again.")
            else -> TokenPoll.Denied(response.errorDescription ?: "GitHub sign-in failed.")
        }
    }

    private fun fetchLogin(token: String): String? {
        val request = Request.Builder()
            .url("https://api.github.com/user")
            .header("Accept", "application/vnd.github+json")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return runCatching { executeToJson<GitHubUser>(request).login }.getOrNull()
    }

    private inline fun <reified T> executeToJson(request: Request): T {
        httpClient.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("GitHub responded HTTP ${response.code}")
            }
            return JSON.decodeFromString(bodyText)
        }
    }

    private data class DeviceCode(
        val deviceCode: String,
        val userCode: String,
        val verificationUri: String,
        val interval: Int,
        val expiresIn: Int,
    )

    private sealed interface TokenPoll {
        data object Pending : TokenPoll
        data object SlowDown : TokenPoll
        data class Denied(val message: String) : TokenPoll
        data class Granted(val accessToken: String) : TokenPoll
    }

    @Serializable
    private data class DeviceCodeResponse(
        val device_code: String? = null,
        val user_code: String? = null,
        val verification_uri: String? = null,
        val expires_in: Int? = null,
        val interval: Int? = null,
    ) {
        val deviceCode get() = device_code
        val userCode get() = user_code
        val verificationUri get() = verification_uri
        val expiresIn get() = expires_in
    }

    @Serializable
    private data class AccessTokenResponse(
        val access_token: String? = null,
        val error: String? = null,
        val error_description: String? = null,
    ) {
        val accessToken get() = access_token
        val errorDescription get() = error_description
    }

    @Serializable
    private data class GitHubUser(val login: String? = null)

    companion object {
        const val GITHUB_HOST = "github.com"
        const val DEFAULT_USERNAME = "x-access-token"
        private const val MIN_POLL_SECONDS = 5
        private const val SLOW_DOWN_STEP_SECONDS = 5
        private const val DEFAULT_EXPIRY_SECONDS = 900
        private const val MAX_CONSECUTIVE_POLL_ERRORS = 6

        private val JSON = Json { ignoreUnknownKeys = true; isLenient = true }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
