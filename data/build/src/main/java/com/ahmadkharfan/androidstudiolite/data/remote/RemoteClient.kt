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
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class RemoteClient(
    private val settings: ServerSettingsRepository,
    private val httpClient: OkHttpClient = defaultClient(),
    private val transferClient: OkHttpClient = defaultTransferClient(),
    private val integrityProvider: IntegrityTokenProvider = NoopIntegrityTokenProvider,
) {

    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val nonceRandom = SecureRandom()

    private suspend fun baseUrl(): String = settings.current().baseUrl.trimEnd('/')


    suspend fun ensureDeviceToken(): String =
        settings.current().deviceToken?.takeIf { it.isNotBlank() } ?: registerDevice()

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


    suspend fun createBuild(request: CreateBuildRequest): CreateBuildResponse {
        val body = RemoteJson.encodeToString(request).toRequestBody(jsonMediaType)
        return authedPost("/v1/builds", body)
    }

    suspend fun startBuild(buildId: String) =
        authedPostUnit("/v1/builds/$buildId/start", EMPTY_BODY)

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


    suspend fun uploadSource(uploadUrl: String, file: File, method: String = "PUT") {
        executeTransferWithRetry {
            val request = Request.Builder()
                .url(uploadUrl)
                .method(method.uppercase(), file.asRequestBody("application/zip".toMediaType()))
                .build()
            transferClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw RemoteException(response.code, null, "Source upload failed (HTTP ${response.code})")
                }
            }
        }
    }

    suspend fun download(downloadUrl: String, dest: File) {
        executeTransferWithRetry {
            val request = Request.Builder().url(downloadUrl).get().build()
            val parent = dest.parentFile ?: error("Download destination has no parent: $dest")
            val temp = File(parent, "${dest.name}.part")
            parent.mkdirs()
            try {
                transferClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw RemoteException(response.code, null, "Artifact download failed (HTTP ${response.code})")
                    }
                    val bodyStream = response.body?.byteStream()
                        ?: throw RemoteException(response.code, null, "Empty artifact response")
                    bodyStream.use { input -> temp.outputStream().use { output -> input.copyTo(output) } }
                }
                if (dest.exists()) dest.delete()
                if (!temp.renameTo(dest)) {
                    temp.inputStream().use { input -> dest.outputStream().use { output -> input.copyTo(output) } }
                    temp.delete()
                }
            } catch (t: Throwable) {
                temp.delete()
                throw t
            }
        }
    }


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


    private suspend inline fun <reified T> authedGet(path: String): T = executeAuthed(path, "GET", null)

    private suspend inline fun <reified T> authedPost(path: String, body: RequestBody): T =
        executeAuthed(path, "POST", body)

    private suspend fun authedPostUnit(path: String, body: RequestBody) {
        executeAuthedRaw(path, "POST", body)
    }

    private suspend inline fun <reified T> executeAuthed(path: String, method: String, body: RequestBody?): T =
        decode(executeAuthedRaw(path, method, body))

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

    private suspend fun executeTransferWithRetry(block: suspend () -> Unit) {
        var attempt = 0
        while (true) {
            try {
                withContext(Dispatchers.IO) { block() }
                return
            } catch (e: IOException) {
                if (attempt >= MAX_ATTEMPTS - 1) {
                    throw RemoteException(0, "NETWORK", networkErrorMessage(e))
                }
            } catch (e: RemoteException) {
                val transient = e.httpStatus == 0 || e.httpStatus in 500..599
                if (!transient || attempt >= MAX_ATTEMPTS - 1) throw e
            }
            delay(BASE_BACKOFF_MS shl attempt)
            attempt++
        }
    }

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
                val mapped = RemoteException(0, "NETWORK", networkErrorMessage(e))
                if (attempt >= MAX_ATTEMPTS - 1) throw mapped
                lastError = mapped
            }
            delay(BASE_BACKOFF_MS shl attempt)
            attempt++
        }
    }

    private fun networkErrorMessage(e: IOException): String {
        val chain = generateSequence<Throwable>(e) { it.cause }.toList()
        for (err in chain) {
            when (err) {
                is java.net.UnknownHostException ->
                    return "You're offline or DNS failed — check your internet connection and try again."
                is java.net.ConnectException ->
                    return "Can't reach the build server — check your internet connection and try again."
                is java.net.NoRouteToHostException ->
                    return "No network route to the build server — check your internet connection."
                is java.net.SocketTimeoutException ->
                    return "Build server timed out — check your internet connection and try again."
            }
        }
        val message = e.message.orEmpty().lowercase()
        return when {
            "unable to resolve host" in message ||
                "failed to connect" in message ||
                "network is unreachable" in message ||
                "connection refused" in message ->
                "You're offline or can't reach the build server — check your internet connection and try again."
            e.message.isNullOrBlank() ->
                "Network error — check your internet connection and try again."
            else -> e.message!!
        }
    }

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
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(0, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()

        fun defaultTransferClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(0, TimeUnit.SECONDS)
            .protocols(listOf(Protocol.HTTP_1_1))
            .build()
    }
}
