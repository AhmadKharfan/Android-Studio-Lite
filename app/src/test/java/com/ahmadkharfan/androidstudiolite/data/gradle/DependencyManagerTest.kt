package com.ahmadkharfan.androidstudiolite.data.gradle

import com.ahmadkharfan.androidstudiolite.data.gradle.deps.DependencyManager
import com.ahmadkharfan.androidstudiolite.data.gradle.parse.VersionCatalogParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DependencyManagerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val manager = DependencyManager()

    private fun buildFile(): File = tmp.newFile("build.gradle.kts").apply {
        writeText(
            """
            plugins { id("com.android.application") }
            dependencies {
                implementation(libs.androidx.core.ktx)
            }
            """.trimIndent(),
        )
    }

    private fun catalogFile(): File = tmp.newFile("libs.versions.toml").apply {
        writeText(
            """
            [versions]
            coreKtx = "1.12.0"
            [libraries]
            androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
            """.trimIndent(),
        )
    }

    @Test
    fun addViaCatalogRoundTrips() {
        val build = buildFile()
        val catalog = catalogFile()

        val outcome = manager.add(
            DependencyManager.AddRequest(
                buildFile = build,
                coordinate = "com.squareup.retrofit2:retrofit:2.11.0",
                strategy = DependencyManager.Strategy.VERSION_CATALOG,
                catalogFile = catalog,
                alias = "retrofit",
            ),
        )
        assertTrue("Expected success but got $outcome", outcome is DependencyManager.Outcome.Success)

        // The catalog now resolves the new alias...
        val cat = VersionCatalogParser.parse(catalog.readText())
        assertEquals("com.squareup.retrofit2:retrofit:2.11.0", cat.findLibrary("retrofit")?.coordinate)

        // ...and the build file references it via the catalog accessor.
        assertTrue(build.readText().contains("implementation(libs.retrofit)"))
    }

    @Test
    fun addDirectWritesCoordinate() {
        val build = buildFile()
        val outcome = manager.add(
            DependencyManager.AddRequest(
                buildFile = build,
                coordinate = "com.google.code.gson:gson:2.10.1",
                strategy = DependencyManager.Strategy.DIRECT,
            ),
        )
        assertTrue(outcome is DependencyManager.Outcome.Success)
        assertTrue(build.readText().contains("implementation(\"com.google.code.gson:gson:2.10.1\")"))
    }

    @Test
    fun malformedCoordinateFails() {
        val outcome = manager.add(
            DependencyManager.AddRequest(buildFile = buildFile(), coordinate = "not-a-coordinate", strategy = DependencyManager.Strategy.DIRECT),
        )
        assertTrue(outcome is DependencyManager.Outcome.Failure)
    }

    @Test
    fun removeFromBuildFile() {
        val build = buildFile()
        manager.add(
            DependencyManager.AddRequest(
                buildFile = build,
                coordinate = "com.google.code.gson:gson:2.10.1",
                strategy = DependencyManager.Strategy.DIRECT,
            ),
        )
        val outcome = manager.removeFromBuildFile(build, "com.google.code.gson:gson")
        assertTrue(outcome is DependencyManager.Outcome.Success)
        assertTrue(!build.readText().contains("gson"))
    }
}
