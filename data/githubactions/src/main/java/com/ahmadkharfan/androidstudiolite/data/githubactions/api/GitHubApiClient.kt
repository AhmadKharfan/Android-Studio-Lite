package com.ahmadkharfan.androidstudiolite.data.githubactions.api

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/**
 * The slice of the GitHub REST API the Actions build provider needs.
 *
 * Reads are retried on transient failures and sent as conditional requests, so an unchanged
 * resource costs nothing against the rate limit while a build is being polled. Writes are never
 * retried here: a repeated workflow dispatch would start a second build.
 *
 * @param token supplies the user's GitHub token, or null when not signed in.
 */
class GitHubApiClient(
    private val token: suspend () -> String?,
    private val httpClient: OkHttpClient = defaultClient(),
    private val baseUrl: HttpUrl = "https://api.github.com/".toHttpUrl(),
    private val waitBeforeRetry: suspend (Long) -> Unit = { delay(it.milliseconds) },
) {

    @Volatile
    var lastRateLimit: RateLimit? = null
        private set

    private val etagCache = object : LinkedHashMap<String, CachedResponse>(ETAG_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CachedResponse>?) =
            size > ETAG_CACHE_SIZE
    }

    suspend fun authenticatedUser(): AuthenticatedUser {
        val response = get("user")
        // GitHub App user tokens and fine-grained tokens have no OAuth scopes: the header is absent or empty.
        val scopes = response.headers["x-oauth-scopes"]?.takeIf { it.isNotBlank() }
            ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty)
        return AuthenticatedUser(JSON.decodeFromString<UserDto>(response.body).login, scopes?.toSet())
    }

    suspend fun repository(owner: String, name: String): GitHubRepository? =
        getOrNull("repos/$owner/$name")?.let { JSON.decodeFromString<GitHubRepository>(it.body) }

    /**
     * Installations of this GitHub App the signed-in user can access. Only works with a GitHub App
     * user token; OAuth tokens get a 403.
     */
    suspend fun userInstallations(): List<AppInstallation> = pages("user/installations") { body ->
        JSON.decodeFromString<InstallationsPage>(body).installations
    }

    /** Repositories [installationId] gives the signed-in user access to. */
    suspend fun installationRepositories(installationId: Long): List<GitHubRepository> =
        pages("user/installations/$installationId/repositories") { body ->
            JSON.decodeFromString<InstallationRepositoriesPage>(body).repositories
        }

    /** Reads up to [MAX_PAGES] pages of [path], stopping at the first page that isn't full. */
    private suspend fun <T> pages(path: String, parse: (String) -> List<T>): List<T> {
        val items = mutableListOf<T>()
        for (page in 1..MAX_PAGES) {
            val batch = parse(get(path, mapOf("per_page" to PAGE_SIZE.toString(), "page" to page.toString())).body)
            items += batch
            if (batch.size < PAGE_SIZE) break
        }
        return items
    }

    suspend fun createUserRepository(name: String, description: String): GitHubRepository {
        val body = JSON.encodeToString(CreateRepositoryRequest(name, description, private = true, autoInit = true))
        return JSON.decodeFromString(send("POST", "user/repos", body).body)
    }

    suspend fun file(owner: String, repo: String, path: String, ref: String? = null): RepositoryFile? {
        val query = ref?.let { mapOf("ref" to it) }.orEmpty()
        val dto = getOrNull("repos/$owner/$repo/contents/$path", query)
            ?.let { JSON.decodeFromString<ContentDto>(it.body) }
            ?: return null
        val content = when (dto.encoding) {
            "base64" -> dto.content.orEmpty().decodeBase64()?.utf8()
                ?: throw GitHubApiException(0, "GitHub returned an unreadable file: $path")
            else -> dto.content.orEmpty()
        }
        return RepositoryFile(dto.path, dto.sha, content)
    }

    /** Creates or replaces a file; [sha] is required to replace an existing one. */
    suspend fun putFile(
        owner: String,
        repo: String,
        path: String,
        content: String,
        message: String,
        sha: String? = null,
        branch: String? = null,
    ) {
        val encoded = content.toByteArray(Charsets.UTF_8).toByteString().base64()
        send("PUT", "repos/$owner/$repo/contents/$path", JSON.encodeToString(PutContentRequest(message, encoded, sha, branch)))
    }

    /** Starts a workflow run and returns its id when GitHub reports it, or null when it doesn't. */
    suspend fun dispatchWorkflow(
        owner: String,
        repo: String,
        workflowFile: String,
        ref: String,
        inputs: Map<String, String>,
    ): Long? {
        val body = JSON.encodeToString(DispatchRequest(ref, inputs))
        val response = send("POST", "repos/$owner/$repo/actions/workflows/$workflowFile/dispatches", body)
        if (response.body.isBlank()) return null
        return runCatching { JSON.decodeFromString<DispatchResponse>(response.body).workflowRunId }.getOrNull()
    }

    /** Recent `workflow_dispatch` runs of [workflowFile], newest first. */
    suspend fun dispatchedRuns(owner: String, repo: String, workflowFile: String, branch: String): List<WorkflowRun> {
        val query = mapOf("event" to "workflow_dispatch", "branch" to branch, "per_page" to "20")
        return JSON.decodeFromString<WorkflowRunsPage>(
            get("repos/$owner/$repo/actions/workflows/$workflowFile/runs", query).body,
        ).workflowRuns
    }

    suspend fun run(owner: String, repo: String, runId: Long): WorkflowRun =
        JSON.decodeFromString(get("repos/$owner/$repo/actions/runs/$runId").body)

    suspend fun jobs(owner: String, repo: String, runId: Long): List<WorkflowJob> =
        JSON.decodeFromString<WorkflowJobsPage>(get("repos/$owner/$repo/actions/runs/$runId/jobs").body).jobs

    suspend fun cancelRun(owner: String, repo: String, runId: Long, force: Boolean = false) {
        send("POST", "repos/$owner/$repo/actions/runs/$runId/${if (force) "force-cancel" else "cancel"}", null)
    }

    suspend fun artifacts(owner: String, repo: String, runId: Long): List<WorkflowArtifact> =
        JSON.decodeFromString<ArtifactsPage>(get("repos/$owner/$repo/actions/runs/$runId/artifacts").body).artifacts

    /** Downloads an artifact's zip archive to [destination], replacing it only once the download completed. */
    suspend fun downloadArtifact(owner: String, repo: String, artifactId: Long, destination: File) {
        stream("repos/$owner/$repo/actions/artifacts/$artifactId/zip", destination)
    }

    suspend fun deleteArtifact(owner: String, repo: String, artifactId: Long) {
        send("DELETE", "repos/$owner/$repo/actions/artifacts/$artifactId", null)
    }

    /** Check runs named [name] on the commit [ref] points at. */
    suspend fun checkRunsNamed(owner: String, repo: String, ref: String, name: String): List<CheckRun> =
        JSON.decodeFromString<CheckRunsPage>(
            get("repos/$owner/$repo/commits/$ref/check-runs", mapOf("check_name" to name)).body,
        ).checkRuns

    suspend fun checkRun(owner: String, repo: String, id: Long): CheckRun =
        JSON.decodeFromString(get("repos/$owner/$repo/check-runs/$id").body)

    /** The plain-text log of a finished job. */
    suspend fun jobLog(owner: String, repo: String, jobId: Long): String =
        get("repos/$owner/$repo/actions/jobs/$jobId/logs", conditional = false).body

    private suspend fun getOrNull(path: String, query: Map<String, String> = emptyMap()): Snapshot? = try {
        get(path, query)
    } catch (e: GitHubApiException) {
        if (e.isNotFound) null else throw e
    }

    private suspend fun get(
        path: String,
        query: Map<String, String> = emptyMap(),
        conditional: Boolean = true,
    ): Snapshot {
        val url = url(path, query)
        var attempt = 0
        while (true) {
            try {
                return execute(request(url, "GET", null), conditional)
            } catch (e: GitHubApiException) {
                if (!e.isTransient || attempt >= MAX_ATTEMPTS - 1) throw e
            }
            waitBeforeRetry(BASE_BACKOFF_MS shl attempt)
            attempt++
        }
    }

    private suspend fun send(method: String, path: String, json: String?): Snapshot {
        val body = json?.toRequestBody(JSON_MEDIA_TYPE) ?: if (method == "DELETE") null else EMPTY_BODY
        return execute(request(url(path, emptyMap()), method, body), conditional = false)
    }

    private suspend fun stream(path: String, destination: File) = withContext(Dispatchers.IO) {
        val parent = destination.absoluteFile.parentFile ?: error("No parent directory for $destination")
        parent.mkdirs()
        val partial = File(parent, "${destination.name}.part")
        try {
            httpClient.newCall(request(url(path, emptyMap()), "GET", null)).execute().use { response ->
                recordRateLimit(response)
                if (!response.isSuccessful) throw toException(response, response.body.string())
                response.body.byteStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
            }
            if (destination.exists() && !destination.delete()) throw IOException("Couldn't replace $destination")
            if (!partial.renameTo(destination)) throw IOException("Couldn't move download to $destination")
        } catch (e: IOException) {
            throw GitHubApiException(0, "Download failed: ${e.message ?: e.javaClass.simpleName}", cause = e)
        } finally {
            partial.delete()
        }
    }

    private suspend fun execute(request: Request, conditional: Boolean): Snapshot = withContext(Dispatchers.IO) {
        val key = request.url.toString()
        val cached = if (conditional) synchronized(etagCache) { etagCache[key] } else null
        val sent = cached?.let { request.newBuilder().header("If-None-Match", it.etag).build() } ?: request
        try {
            httpClient.newCall(sent).execute().use { response ->
                recordRateLimit(response)
                val body = response.body.string()
                when {
                    response.code == HTTP_NOT_MODIFIED && cached != null -> cached.snapshot
                    response.isSuccessful -> Snapshot(response.code, body, response.headers.toMultimap().mapValues { it.value.first() })
                        .also { snapshot ->
                            val etag = response.header("ETag")
                            if (conditional && etag != null) {
                                synchronized(etagCache) { etagCache[key] = CachedResponse(etag, snapshot) }
                            }
                        }
                    else -> throw toException(response, body)
                }
            }
        } catch (e: IOException) {
            throw GitHubApiException(0, "Couldn't reach GitHub: ${e.message ?: e.javaClass.simpleName}", cause = e)
        }
    }

    private suspend fun request(url: HttpUrl, method: String, body: RequestBody?): Request {
        val token = token()?.takeIf { it.isNotBlank() }
            ?: throw GitHubApiException(401, "Sign in to GitHub to build with GitHub Actions.")
        return Request.Builder()
            .url(url)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", API_VERSION)
            .header("User-Agent", USER_AGENT)
            .header("Authorization", "Bearer $token")
            .method(method, body)
            .build()
    }

    private fun url(path: String, query: Map<String, String>): HttpUrl =
        baseUrl.newBuilder().addPathSegments(path).apply {
            query.forEach { (name, value) -> addQueryParameter(name, value) }
        }.build()

    private fun recordRateLimit(response: Response) {
        val limit = response.header("X-RateLimit-Limit")?.toIntOrNull() ?: return
        val remaining = response.header("X-RateLimit-Remaining")?.toIntOrNull() ?: return
        val reset = response.header("X-RateLimit-Reset")?.toLongOrNull() ?: return
        lastRateLimit = RateLimit(limit, remaining, reset)
    }

    private fun toException(response: Response, body: String): GitHubApiException {
        val message = runCatching { JSON.decodeFromString<ErrorDto>(body).message }.getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?: "GitHub responded HTTP ${response.code}"
        val retryAfter = response.header("Retry-After")?.toLongOrNull()
        val exhausted = response.header("X-RateLimit-Remaining") == "0" &&
            (response.code == HTTP_FORBIDDEN || response.code == HTTP_TOO_MANY_REQUESTS)
        val reset = if (exhausted) response.header("X-RateLimit-Reset")?.toLongOrNull() else null
        val acceptedPermissions = response.header("X-Accepted-GitHub-Permissions")?.takeIf { it.isNotBlank() }
        return GitHubApiException(response.code, message, retryAfter, reset, acceptedPermissions = acceptedPermissions)
    }

    /** A successful response, detached from the connection. Header names are lower case. */
    internal data class Snapshot(val code: Int, val body: String, val headers: Map<String, String>)

    private data class CachedResponse(val etag: String, val snapshot: Snapshot)

    companion object {
        private const val API_VERSION = "2022-11-28"
        private const val USER_AGENT = "AndroidStudioLite"
        private const val MAX_ATTEMPTS = 3
        private const val BASE_BACKOFF_MS = 1_000L
        private const val ETAG_CACHE_SIZE = 64
        private const val PAGE_SIZE = 100
        private const val MAX_PAGES = 10
        private const val HTTP_NOT_MODIFIED = 304
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_TOO_MANY_REQUESTS = 429
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private val EMPTY_BODY = ByteArray(0).toRequestBody(null)

        internal val JSON = Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }
}
