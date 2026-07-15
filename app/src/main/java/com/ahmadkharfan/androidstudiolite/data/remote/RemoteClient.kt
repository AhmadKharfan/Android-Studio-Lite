package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.data.remote.protocol.ArtifactResponse
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.BuildStateResponse
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.BuildStatusResponse
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.CreateBuildRequest
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.CreateBuildResponse
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.ErrorEnvelope
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.RegisterDeviceRequest
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.RegisterDeviceResponse
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.RemoteJson
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.WireProjectModel
import com.ahmadkharfan.androidstudiolite.data.remote.attestation.IntegrityTokenProvider
import com.ahmadkharfan.androidstudiolite.data.remote.attestation.NoopIntegrityTokenProvider
import java.io.File
import java.io.IOException
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * OkHttp transport for the control-plane build API (`/v1`) plus the build-event WebSocket. Every
 * authenticated call carries `Authorization: Bearer <deviceToken>`; the token is minted lazily via
 * [ensureDeviceToken] (`POST /v1/devices`) and cached in [ServerSettingsRepository]. Idempotent REST
 * calls retry with exponential backoff on transient network/5xx failures; a 401 clears the cached
 * token and re-registers once. The base URL is read from settings on every call, so a change in the
 * server-settings screen takes effect immediately.
 *
 * This is a thin, stateless transport: [RemoteBuildSystem] orchestrates the build lifecycle on it.
 */
class RemoteClient(
    private val settings: ServerSettingsRepository,
    private val httpClient: OkHttpClient = defaultClient(),
    private val integrityProvider: IntegrityTokenProvider = NoopIntegrityTokenProvider,
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val nonceRandom = SecureRandom()

    private suspend fun baseUrl(): String = settings.current().baseUrl.trimEnd('/')

    // ---------------------------------------------------------------- devices

    /** Returns a cached device token, minting one via [registerDevice] on first use. */
    suspend fun ensureDeviceToken(): String =
        settings.current().deviceToken?.takeIf { it.isNotBlank() } ?: registerDevice()

    /**
     * Registers this device (`POST /v1/devices`), persists and returns the minted token. Attaches a
     * best-effort Play Integrity token (null when attestation is disabled/unavailable — see
     * [IntegrityTokenProvider]); the request never fails just because attestation couldn't be produced.
     */
    suspend fun registerDevice(): String {
        val integrityToken = integrityProvider.requestToken(newNonce())
        val body = RemoteJson.encodeToString(RegisterDeviceRequest(integrityToken)).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url("${baseUrl()}/v1/devices")
            .post(body)
            .build()
        val snapshot = executeWithRetry(request, allowUnauthorizedThrow = false)
        val token = decode<RegisterDeviceResponse>(snapshot).deviceToken
        settings.setDeviceToken(token)
        return token
    }

    // ---------------------------------------------------------------- builds

    suspend fun createBuild(request: CreateBuildRequest): CreateBuildResponse {
        val body = RemoteJson.encodeToString(request).toRequestBody(jsonMediaType)
        return authedPost("/v1/builds", body)
    }

    /**
     * Enqueue an already-uploaded build. The response body is deliberately NOT parsed: the control
     * plane answers `202 {"status":"queued"}` with no `buildId` (the caller already has it), so
     * decoding it as [BuildStateResponse] threw MissingFieldException('buildId') and killed the
     * build. Success is the 2xx itself — [authedPostUnit] still raises RemoteException on 4xx/5xx.
     */
    suspend fun startBuild(buildId: String) =
        authedPostUnit("/v1/builds/$buildId/start", EMPTY_BODY)

    /** Same contract as [startBuild]: the control plane returns a bare status object. */
    suspend fun cancelBuild(buildId: String) =
        authedPostUnit("/v1/builds/$buildId/cancel", EMPTY_BODY)

    suspend fun buildStatus(buildId: String): BuildStatusResponse =
        authedGet("/v1/builds/$buildId")

    suspend fun artifact(buildId: String): ArtifactResponse =
        authedGet("/v1/builds/$buildId/artifact")

    suspend fun sync(request: CreateBuildRequest): WireProjectModel {
        val body = RemoteJson.encodeToString(request).toRequestBody(jsonMediaType)
        return authedPost("/v1/sync", body)
    }

    // ---------------------------------------------------------------- transfer

    /** Streams [file] to a presigned upload URL (default `PUT`). No auth header — the URL is signed. */
    suspend fun uploadSource(uploadUrl: String, file: File, method: String = "PUT") = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(uploadUrl)
            .method(method.uppercase(), file.asRequestBody("application/zip".toMediaType()))
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RemoteException(response.code, null, "Source upload failed (HTTP ${response.code})")
            }
        }
    }

    /** Downloads a presigned GET URL to [dest], streaming to disk. No auth header — the URL is signed. */
    suspend fun download(downloadUrl: String, dest: File) = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(downloadUrl).get().build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RemoteException(response.code, null, "Artifact download failed (HTTP ${response.code})")
            }
            val bodyStream = response.body?.byteStream()
                ?: throw RemoteException(response.code, null, "Empty artifact response")
            dest.parentFile?.mkdirs()
            bodyStream.use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
        }
    }

    // ---------------------------------------------------------------- websocket

    /**
     * Opens `WS /v1/builds/{id}/stream`. The token is passed both as an `Authorization` header and a
     * `?token=` query param (some proxies strip WS upgrade headers). The caller owns the returned
     * socket and must close it. Reconnection/backoff is the caller's concern.
     */
    suspend fun openStream(buildId: String, listener: WebSocketListener): WebSocket {
        val token = ensureDeviceToken()
        val streamUrl = baseUrl().toHttpUrl().newBuilder()
            .addPathSegments("v1/builds/$buildId/stream")
            .addQueryParameter("token", token)
            .build()
        val request = Request.Builder()
            .url(streamUrl)
            .header("Authorization", "Bearer $token")
            .build()
        return httpClient.newWebSocket(request, listener)
    }

    // ---------------------------------------------------------------- internals

    private suspend inline fun <reified T> authedGet(path: String): T = executeAuthed(path, "GET", null)

    private suspend inline fun <reified T> authedPost(path: String, body: RequestBody): T =
        executeAuthed(path, "POST", body)

    /**
     * POST where only the status code matters. Use for endpoints whose body is an unmodelled
     * acknowledgement (e.g. `{"status":"queued"}`) — decoding those into a DTO with required
     * fields throws MissingFieldException even though the call succeeded.
     */
    private suspend fun authedPostUnit(path: String, body: RequestBody) {
        executeAuthedRaw(path, "POST", body)
    }

    /** Executes an authed call, retrying once with a fresh token on 401. */
    private suspend inline fun <reified T> executeAuthed(path: String, method: String, body: RequestBody?): T =
        decode(executeAuthedRaw(path, method, body))

    /** [executeAuthed] without the decode step — for callers that ignore the body. */
    @PublishedApi
    internal suspend fun executeAuthedRaw(path: String, method: String, body: RequestBody?): ResponseSnapshot {
        val base = baseUrl()
        val token = ensureDeviceToken()
        return try {
            executeWithRetry(request(base, path, method, body, token), allowUnauthorizedThrow = true)
        } catch (e: RemoteException) {
            if (!e.isUnauthorized) throw e
            settings.clearDeviceToken()
            val fresh = registerDevice()
            executeWithRetry(request(base, path, method, body, fresh), allowUnauthorizedThrow = false)
        }
    }

    private fun request(base: String, path: String, method: String, body: RequestBody?, token: String): Request =
        Request.Builder()
            .url("$base$path")
            .header("Authorization", "Bearer $token")
            .method(method, body)
            .build()

    /** Executes with exponential backoff on transient IO / 5xx failures. */
    private suspend fun executeWithRetry(request: Request, allowUnauthorizedThrow: Boolean): ResponseSnapshot {
        var attempt = 0
        var lastError: RemoteException? = null
        while (true) {
            try {
                val snapshot = withContext(Dispatchers.IO) {
                    httpClient.newCall(request).execute().use { it.toSnapshot() }
                }
                if (snapshot.isSuccessful) return snapshot
                val ex = snapshot.toException()
                val transient = snapshot.code in 500..599
                if (snapshot.code == 401 && allowUnauthorizedThrow) throw ex
                if (!transient || attempt >= MAX_ATTEMPTS - 1) throw ex
                lastError = ex
            } catch (e: IOException) {
                if (attempt >= MAX_ATTEMPTS - 1) throw RemoteException(0, null, e.message ?: "Network error")
                lastError = RemoteException(0, null, e.message ?: "Network error")
            }
            delay(BASE_BACKOFF_MS shl attempt)
            attempt++
        }
    }

    /** A fresh random request-hash/nonce for Play Integrity, lowercase hex (URL/JSON-safe). */
    private fun newNonce(): String {
        val bytes = ByteArray(16).also(nonceRandom::nextBytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private inline fun <reified T> decode(snapshot: ResponseSnapshot): T =
        RemoteJson.decodeFromString(snapshot.body)

    private fun Response.toSnapshot(): ResponseSnapshot =
        ResponseSnapshot(code = code, isSuccessful = isSuccessful, body = body?.string().orEmpty())

    @PublishedApi
    internal data class ResponseSnapshot(val code: Int, val isSuccessful: Boolean, val body: String) {
        fun toException(): RemoteException {
            val err = runCatching { RemoteJson.decodeFromString<ErrorEnvelope>(body).error }.getOrNull()
            return RemoteException(code, err?.code, err?.message ?: "HTTP $code")
        }
    }

    companion object {
        private const val MAX_ATTEMPTS = 3
        private const val BASE_BACKOFF_MS = 500L
        private val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(null)

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS) // no read timeout — build streams run for minutes
            .writeTimeout(0, TimeUnit.SECONDS) // large source uploads
            .pingInterval(20, TimeUnit.SECONDS) // keep the build-event WS alive
            .build()
    }
}
