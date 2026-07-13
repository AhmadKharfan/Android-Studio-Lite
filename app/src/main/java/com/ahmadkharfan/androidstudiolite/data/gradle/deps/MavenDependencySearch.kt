package com.ahmadkharfan.androidstudiolite.data.gradle.deps

import com.ahmadkharfan.androidstudiolite.data.gradle.util.MiniJson
import java.io.IOException
import java.net.URLEncoder
import okhttp3.OkHttpClient
import okhttp3.Request

/** One artifact returned by a Maven Central search. */
data class MavenArtifact(
    val group: String,
    val artifact: String,
    val latestVersion: String?,
    val packaging: String?,
) {
    /** `group:artifact:latestVersion` when a version is known, else `group:artifact`. */
    val coordinate: String
        get() = if (latestVersion.isNullOrBlank()) "$group:$artifact" else "$group:$artifact:$latestVersion"
}

/** Abstraction over the one HTTP GET we make, so search is unit-testable without the network. */
fun interface HttpGet {
    /** Returns the response body, or throws [IOException] on failure. */
    fun get(url: String): String
}

/**
 * Discovery against the Maven Central REST API (`search.maven.org/solrsearch`). Used by the
 * add-dependency UI to find coordinates and their available versions. No parsing of Gradle files
 * here — this only turns a user query into candidate coordinates.
 */
class MavenDependencySearch(
    private val http: HttpGet = OkHttpGet(),
    private val baseUrl: String = "https://search.maven.org/solrsearch/select",
) {

    /** Free-text search; returns up to [rows] artifacts, newest-per-artifact (Solr `latestVersion`). */
    fun search(query: String, rows: Int = 20): List<MavenArtifact> {
        if (query.isBlank()) return emptyList()
        val url = "$baseUrl?q=${encode(query)}&rows=$rows&wt=json"
        return parseDocs(http.get(url)) { doc ->
            val g = doc["g"] as? String ?: return@parseDocs null
            val a = doc["a"] as? String ?: return@parseDocs null
            MavenArtifact(g, a, doc["latestVersion"] as? String, doc["p"] as? String)
        }
    }

    /** All published versions of a specific `group:artifact`, newest first. */
    fun versions(group: String, artifact: String, rows: Int = 50): List<String> {
        val q = "g:\"$group\" AND a:\"$artifact\""
        val url = "$baseUrl?q=${encode(q)}&core=gav&rows=$rows&wt=json"
        return parseDocs(http.get(url)) { doc -> doc["v"] as? String }.filterNotNull()
    }

    private fun <T> parseDocs(body: String, map: (Map<String, Any?>) -> T?): List<T> {
        val root = runCatching { MiniJson.parse(body) }.getOrNull() as? Map<*, *> ?: return emptyList()
        val response = root["response"] as? Map<*, *> ?: return emptyList()
        val docs = response["docs"] as? List<*> ?: return emptyList()
        return docs.mapNotNull { (it as? Map<*, *>)?.let { m -> map(coerce(m)) } }
    }

    @Suppress("UNCHECKED_CAST")
    private fun coerce(m: Map<*, *>): Map<String, Any?> = m as Map<String, Any?>

    private fun encode(s: String): String = URLEncoder.encode(s, "UTF-8")

    /** Default OkHttp-backed [HttpGet]. */
    class OkHttpGet(private val client: OkHttpClient = OkHttpClient()) : HttpGet {
        override fun get(url: String): String {
            val request = Request.Builder().url(url).header("Accept", "application/json").build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException("HTTP ${response.code} for $url")
                return response.body?.string() ?: throw IOException("Empty body for $url")
            }
        }
    }
}
