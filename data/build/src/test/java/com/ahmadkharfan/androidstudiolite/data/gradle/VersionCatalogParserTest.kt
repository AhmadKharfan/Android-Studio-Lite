package com.ahmadkharfan.androidstudiolite.data.gradle

import com.ahmadkharfan.androidstudiolite.data.gradle.parse.VersionCatalogParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VersionCatalogParserTest {

    private val toml = """
        [versions]
        coreKtx = "1.12.0"
        kotlin = "2.0.0"

        [libraries]
        androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
        retrofit = { module = "com.squareup.retrofit2:retrofit", version = "2.11.0" }
        okhttp = "com.squareup.okhttp3:okhttp:4.12.0"
        junit = { group = "junit", name = "junit" }

        [plugins]
        android-application = { id = "com.android.application", version.ref = "kotlin" }
        kotlin-compose = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }

        [bundles]
        networking = ["retrofit", "okhttp"]
    """.trimIndent()

    @Test
    fun versionsParsed() {
        val cat = VersionCatalogParser.parse(toml)
        assertEquals("1.12.0", cat.versions["coreKtx"])
        assertEquals("2.0.0", cat.versions["kotlin"])
    }

    @Test
    fun libraryFormsResolved() {
        val cat = VersionCatalogParser.parse(toml)
        assertEquals("androidx.core:core-ktx:1.12.0", cat.findLibrary("androidx.core.ktx")?.coordinate)
        assertEquals("com.squareup.retrofit2:retrofit:2.11.0", cat.findLibrary("retrofit")?.coordinate)
        assertEquals("com.squareup.okhttp3:okhttp:4.12.0", cat.findLibrary("okhttp")?.coordinate)

        assertEquals("junit:junit", cat.findLibrary("junit")?.coordinate)
    }

    @Test
    fun aliasSeparatorsAreInterchangeable() {
        val cat = VersionCatalogParser.parse(toml)
        val byDots = cat.findLibrary("androidx.core.ktx")
        val byDashes = cat.findLibrary("androidx-core-ktx")
        assertEquals(byDots, byDashes)
    }

    @Test
    fun pluginsResolved() {
        val cat = VersionCatalogParser.parse(toml)
        assertEquals("com.android.application", cat.findPlugin("android.application")?.id)
        assertEquals("org.jetbrains.kotlin.plugin.compose", cat.findPlugin("kotlin.compose")?.id)
    }

    @Test
    fun bundlesResolved() {
        val cat = VersionCatalogParser.parse(toml)
        assertEquals(listOf("retrofit", "okhttp"), cat.findBundle("networking"))
    }

    @Test
    fun unknownAliasReturnsNull() {
        val cat = VersionCatalogParser.parse(toml)
        assertNull(cat.findLibrary("does.not.exist"))
    }
}
