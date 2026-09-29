package com.ahmadkharfan.androidstudiolite.data.githubactions.build

import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.concurrent.CopyOnWriteArrayList
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject

/**
 * A scripted GitHub REST API for one user (`octo`) and one build repository. Tests shape the scenario
 * through the mutable fields, then read what the app asked for from [requests].
 */
internal class FakeGitHub {

    val server = MockWebServer()
    val requests = CopyOnWriteArrayList<Pair<String, String>>()

    var scopes: String? = "repo, workflow"
    var repositoryExists = false
    var repositoryPrivate = true
    var workflowContent: String? = null
    var dispatchReturnsRunId = true
    var runStatuses = ArrayDeque(listOf("queued", "in_progress", "completed"))
    var conclusion = "success"
    var resultZip: ByteArray? = resultZip()
    var runListed = true

    /** Successive `(asl-lines, tail)` states of the live-log check run; empty means no live log. */
    var liveOutputs = ArrayDeque<Pair<Int, String>>()
    private var lastLive: Pair<Int, String>? = null

    private var lastStatus = "queued"

    fun start() = server.apply {
        dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = request.body.readUtf8()
                requests += "${request.method} ${request.path}" to body
                return route(request.method.orEmpty(), request.path.orEmpty().substringBefore('?'))
            }
        }
        start()
    }

    fun paths(): List<String> = requests.map { it.first }

    fun bodyOf(prefix: String): String = requests.first { it.first.startsWith(prefix) }.second

    @Suppress("CyclomaticComplexMethod")
    private fun route(method: String, path: String): MockResponse = when {
        method == "GET" && path == "/user" ->
            json("""{"login":"octo"}""").apply { scopes?.let { setHeader("X-OAuth-Scopes", it) } }
        method == "GET" && path == "/repos/octo/asl-build" ->
            if (repositoryExists) json(repoJson()) else json("""{"message":"Not Found"}""", 404)
        method == "POST" && path == "/user/repos" -> {
            repositoryExists = true
            json(repoJson(), 201)
        }
        method == "GET" && path == "/repos/octo/asl-build/contents/.github/workflows/asl-build.yml" ->
            workflowContent?.let {
                json("""{"path":".github/workflows/asl-build.yml","sha":"wf1","content":"${Buffer().writeUtf8(it).readByteString().base64()}","encoding":"base64"}""")
            } ?: json("""{"message":"Not Found"}""", 404)
        method == "PUT" && path == "/repos/octo/asl-build/contents/.github/workflows/asl-build.yml" -> {
            workflowContent = "written"
            json("""{"content":{}}""", 201)
        }
        method == "POST" && path == "/repos/octo/asl-build/actions/workflows/asl-build.yml/dispatches" ->
            if (dispatchReturnsRunId) json("""{"workflow_run_id":77,"run_url":"u","html_url":"h"}""") else MockResponse().setResponseCode(204)
        method == "GET" && path == "/repos/octo/asl-build/actions/workflows/asl-build.yml/runs" ->
            json(if (runListed) """{"workflow_runs":[{"id":77,"display_title":"asl-$CORRELATION","status":"queued"}]}""" else """{"workflow_runs":[]}""")
        method == "GET" && path == "/repos/octo/asl-build/actions/runs/77" -> {
            lastStatus = runStatuses.removeFirstOrNull() ?: lastStatus
            val conclusionJson = if (lastStatus == "completed") "\"$conclusion\"" else "null"
            json("""{"id":77,"status":"$lastStatus","conclusion":$conclusionJson,"html_url":"https://github.com/octo/asl-build/actions/runs/77"}""")
        }
        method == "GET" && path == "/repos/octo/asl-build/actions/runs/77/jobs" ->
            json("""{"jobs":[{"id":5,"name":"Build","status":"in_progress","steps":[{"name":"Build","number":7,"status":"in_progress"}]}]}""")
        method == "POST" && path == "/repos/octo/asl-build/actions/runs/77/cancel" -> MockResponse().setResponseCode(202)
        method == "GET" && path == "/repos/octo/asl-build/commits/main/check-runs" ->
            json(if (liveOutputs.isEmpty() && lastLive == null) """{"check_runs":[]}""" else """{"check_runs":[{"id":55,"name":"asl-live-$CORRELATION"}]}""")
        method == "GET" && path == "/repos/octo/asl-build/check-runs/55" -> {
            val (lines, text) = (liveOutputs.removeFirstOrNull() ?: lastLive ?: (0 to "")).also { lastLive = it }
            json(buildJsonObject {
                put("id", 55)
                put("name", "asl-live-$CORRELATION")
                putJsonObject("output") {
                    put("summary", "asl-lines $lines")
                    put("text", text)
                }
            }.toString())
        }
        method == "GET" && path == "/repos/octo/asl-build/actions/runs/77/artifacts" ->
            json(if (resultZip != null) """{"artifacts":[{"id":9,"name":"asl-result","size_in_bytes":10}]}""" else """{"artifacts":[]}""")
        method == "GET" && path == "/repos/octo/asl-build/actions/artifacts/9/zip" ->
            MockResponse().setBody(Buffer().write(checkNotNull(resultZip)))
        method == "DELETE" && path == "/repos/octo/asl-build/actions/artifacts/9" -> MockResponse().setResponseCode(204)
        else -> json("""{"message":"Unexpected $method $path"}""", 500)
    }

    private fun repoJson() =
        """{"full_name":"octo/asl-build","private":$repositoryPrivate,"default_branch":"main"}"""

    private fun json(body: String, code: Int = 200) =
        MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body)

    companion object {
        const val CORRELATION = "corr-0001-abcd"
        val APK_BYTES = "fake-apk-bytes".toByteArray()

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        fun resultZip(
            success: Boolean = true,
            log: String = "> Task :app:assembleDebug\nBUILD SUCCESSFUL",
            apkSha: String = sha256(APK_BYTES),
            extraEntry: String? = null,
        ): ByteArray {
            val manifest = """{"protocol":1,"success":$success,"artifacts":""" +
                (if (success) """[{"name":"app-debug.apk","kind":"APK","sizeBytes":${APK_BYTES.size},"sha256":"$apkSha"}]""" else "[]") + "}"
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                fun put(name: String, bytes: ByteArray) {
                    zip.putNextEntry(ZipEntry(name))
                    zip.write(bytes)
                    zip.closeEntry()
                }
                put("asl-result.json", manifest.toByteArray())
                put("build.log", log.toByteArray())
                put("events.ndjson", """{"type":"taskFinished","taskPath":":app:compileDebugKotlin","result":"SUCCESS"}""".toByteArray())
                if (success) put("artifacts/app-debug.apk", APK_BYTES)
                extraEntry?.let { put(it, "x".toByteArray()) }
            }
            return out.toByteArray()
        }
    }
}
