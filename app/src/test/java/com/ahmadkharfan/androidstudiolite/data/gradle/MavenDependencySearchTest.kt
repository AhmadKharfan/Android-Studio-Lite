package com.ahmadkharfan.androidstudiolite.data.gradle

import com.ahmadkharfan.androidstudiolite.data.gradle.deps.MavenDependencySearch
import com.ahmadkharfan.androidstudiolite.data.gradle.util.MiniJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MavenDependencySearchTest {

    @Test
    fun miniJsonParsesNestedStructures() {
        val json = """{"a":1,"b":[true,null,"x"],"c":{"d":2.5}}"""
        @Suppress("UNCHECKED_CAST")
        val map = MiniJson.parse(json) as Map<String, Any?>
        assertEquals(1.0, map["a"])
        assertEquals(listOf(true, null, "x"), map["b"])
        assertEquals(2.5, (map["c"] as Map<*, *>)["d"])
    }

    @Test
    fun searchParsesArtifacts() {
        val body = """
            {"responseHeader":{"status":0},"response":{"numFound":2,"start":0,"docs":[
              {"id":"androidx.core:core-ktx","g":"androidx.core","a":"core-ktx","latestVersion":"1.12.0","p":"aar"},
              {"id":"com.squareup.okhttp3:okhttp","g":"com.squareup.okhttp3","a":"okhttp","latestVersion":"4.12.0","p":"jar"}
            ]}}
        """.trimIndent()
        val search = MavenDependencySearch(http = { body })
        val results = search.search("core-ktx")
        assertEquals(2, results.size)
        assertEquals("androidx.core:core-ktx:1.12.0", results[0].coordinate)
        assertEquals("aar", results[0].packaging)
        assertEquals("com.squareup.okhttp3:okhttp:4.12.0", results[1].coordinate)
    }

    @Test
    fun versionsParsesGavDocs() {
        val body = """
            {"response":{"numFound":3,"docs":[
              {"v":"4.12.0"},{"v":"4.11.0"},{"v":"4.10.0"}
            ]}}
        """.trimIndent()
        val search = MavenDependencySearch(http = { body })
        assertEquals(listOf("4.12.0", "4.11.0", "4.10.0"), search.versions("com.squareup.okhttp3", "okhttp"))
    }

    @Test
    fun blankQueryShortCircuits() {
        var called = false
        val search = MavenDependencySearch(http = { called = true; "" })
        assertTrue(search.search("   ").isEmpty())
        assertTrue(!called)
    }

    @Test
    fun malformedResponseYieldsEmpty() {
        val search = MavenDependencySearch(http = { "not json at all" })
        assertTrue(search.search("x").isEmpty())
    }
}
