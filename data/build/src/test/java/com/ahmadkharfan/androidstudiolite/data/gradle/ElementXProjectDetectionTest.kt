package com.ahmadkharfan.androidstudiolite.data.gradle

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ElementXProjectDetectionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val reader = GradleProjectReader()

    private fun materializeElementX(): File {
        val root = tmp.newFolder("element-x")
        copyResource("element-x/settings.gradle.kts", File(root, "settings.gradle.kts"))
        copyResource("element-x/app/build.gradle.kts", File(root, "app/build.gradle.kts"))
        copyResource("element-x/gradle/libs.versions.toml", File(root, "gradle/libs.versions.toml"))
        return root
    }

    private fun copyResource(path: String, dest: File) {
        val stream = javaClass.classLoader?.getResourceAsStream(path)
            ?: error("Missing test resource: $path")
        dest.parentFile?.mkdirs()
        stream.use { input -> dest.outputStream().use { input.copyTo(it) } }
    }

    @Test
    fun appModuleIsTypedAsAndroidAppDespiteConventionPlugin() {
        val app = reader.read(materializeElementX()).model.modules.first { it.path == ":app" }
        assertEquals(ModuleType.ANDROID_APP, app.type)
    }

    @Test
    fun detectsFlavoredVariantsAndExactAssembleTasks() {
        val app = reader.read(materializeElementX()).model.modules.first { it.path == ":app" }
        val names = app.variants.map { it.name }.toSet()

        assertTrue("expected gplayDebug in $names", names.contains("gplayDebug"))
        assertTrue(names.contains("gplayRelease"))
        assertTrue(names.contains("gplayNightly"))
        assertTrue(names.contains("fdroidDebug"))
        assertTrue(names.contains("fdroidRelease"))
        assertTrue(names.contains("fdroidNightly"))

        assertTrue("should not expose an un-flavored 'debug' variant", !names.contains("debug"))
        assertTrue(!names.contains("release"))

        val gplayDebug = app.variants.first { it.name == "gplayDebug" }
        assertEquals(":app:assembleGplayDebug", gplayDebug.assembleTaskPath)
        assertEquals(":app:bundleGplayDebug", gplayDebug.bundleTaskPath)
        assertEquals("debug", gplayDebug.buildType)
        assertEquals(listOf("gplay"), gplayDebug.flavors)
    }

    @Test
    fun resolvesAgpVersionFromCatalog() {
        val result = reader.read(materializeElementX())
        assertEquals("9.2.1", result.agpVersion)
    }
}
