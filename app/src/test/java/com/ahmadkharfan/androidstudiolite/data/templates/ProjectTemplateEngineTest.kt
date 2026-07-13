package com.ahmadkharfan.androidstudiolite.data.templates

import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.data.gradle.model.DiagnosticSeverity
import com.ahmadkharfan.androidstudiolite.data.gradle.parse.BuildGradleParser
import com.ahmadkharfan.androidstudiolite.data.gradle.model.GradleDsl
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import com.ahmadkharfan.androidstudiolite.domain.model.NewProjectSpec
import com.ahmadkharfan.androidstudiolite.domain.model.ProjectBuildDsl
import com.ahmadkharfan.androidstudiolite.domain.model.TemplateLanguage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectTemplateEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val engine = ProjectTemplateEngine()
    private val reader = GradleProjectReader()

    private fun generate(spec: NewProjectSpec): File {
        val dir = tmp.newFolder(spec.name.lowercase().replace(' ', '-') + "-" + spec.templateId)
        engine.generate(spec, dir)
        return dir
    }

    private fun appModule(dir: File): ModuleModel {
        val result = reader.read(dir)
        // Every generated project must parse without a single ERROR diagnostic.
        assertTrue(
            "unexpected parse errors: ${result.diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }}",
            result.diagnostics.none { it.severity == DiagnosticSeverity.ERROR },
        )
        assertEquals("8.7", result.gradleVersion)
        assertNotNull("version catalog should parse", result.catalog)
        return result.model.modules.first { it.path == ":app" }
    }

    @Test
    fun `every shipped template generates a coherent android app project`() {
        for (template in TemplateRegistry.DEFAULT) {
            val spec = NewProjectSpec(
                name = "Sample",
                packageName = "com.example.sample",
                templateId = template.metadata.id,
                minSdk = 24,
                targetSdk = 34,
            )
            val dir = generate(spec)

            // Shared scaffolding is always present.
            assertTrue("${template.metadata.id}: settings", File(dir, "settings.gradle.kts").isFile)
            assertTrue("${template.metadata.id}: root build", File(dir, "build.gradle.kts").isFile)
            assertTrue("${template.metadata.id}: app build", File(dir, "app/build.gradle.kts").isFile)
            assertTrue("${template.metadata.id}: catalog", File(dir, "gradle/libs.versions.toml").isFile)
            assertTrue("${template.metadata.id}: wrapper", File(dir, "gradle/wrapper/gradle-wrapper.properties").isFile)
            assertTrue("${template.metadata.id}: manifest", File(dir, "app/src/main/AndroidManifest.xml").isFile)

            // The static reader maps it into a coherent app module.
            val app = appModule(dir)
            assertEquals("${template.metadata.id}: type", ModuleType.ANDROID_APP, app.type)
            assertTrue("${template.metadata.id}: has variants", app.variants.isNotEmpty())
            assertTrue("${template.metadata.id}: has dependencies", app.dependencies.isNotEmpty())

            // The android {} block carries the requested identity + SDK levels.
            val android = BuildGradleParser.parse(File(dir, "app/build.gradle.kts").readText(), GradleDsl.KOTLIN).android
            assertNotNull("${template.metadata.id}: android block", android)
            assertEquals("${template.metadata.id}: namespace", "com.example.sample", android!!.namespace)
            assertEquals("${template.metadata.id}: minSdk", "24", android.minSdk)
            assertEquals("${template.metadata.id}: targetSdk", "34", android.targetSdk)
            assertEquals("${template.metadata.id}: compileSdk", "34", android.compileSdk)
        }
    }

    @Test
    fun `empty compose template wires compose deps and a theme package`() {
        val dir = generate(NewProjectSpec("Composer", "com.example.composer", "empty-compose"))

        val app = appModule(dir)
        assertTrue(app.dependencies.any { it.coordinate == "androidx.core:core-ktx:1.13.1" })
        assertTrue(app.dependencies.any { it.coordinate == "androidx.compose:compose-bom:2024.06.00" })
        // BOM-governed artifacts are versionless (no double-pinned version).
        assertTrue(app.dependencies.any { it.coordinate == "androidx.compose.material3:material3" })

        val appBuild = File(dir, "app/build.gradle.kts").readText()
        assertTrue("compose enabled", appBuild.contains("compose = true"))
        assertTrue("compose compiler pinned", appBuild.contains("kotlinCompilerExtensionVersion = \"1.5.14\""))

        assertTrue(File(dir, "app/src/main/java/com/example/composer/MainActivity.kt").isFile)
        assertTrue(File(dir, "app/src/main/java/com/example/composer/ui/theme/Theme.kt").isFile)
        assertTrue(File(dir, "app/src/main/java/com/example/composer/ui/theme/Color.kt").isFile)
    }

    @Test
    fun `native cpp template emits cmake and jni sources`() {
        val dir = generate(NewProjectSpec("Nate", "com.example.nate", "native-cpp"))

        assertTrue(File(dir, "app/src/main/cpp/CMakeLists.txt").isFile)
        assertTrue(File(dir, "app/src/main/cpp/native-lib.cpp").isFile)
        val appBuild = File(dir, "app/build.gradle.kts").readText()
        assertTrue("externalNativeBuild wired", appBuild.contains("externalNativeBuild"))
        assertTrue("cmake path", appBuild.contains("src/main/cpp/CMakeLists.txt"))
        // JNI symbol must match the package.
        assertTrue(
            File(dir, "app/src/main/cpp/native-lib.cpp").readText()
                .contains("Java_com_example_nate_MainActivity_stringFromJNI"),
        )
    }

    @Test
    fun `no activity template omits the launcher activity`() {
        val dir = generate(NewProjectSpec("Empty", "com.example.empty", "no-activity"))

        val manifest = File(dir, "app/src/main/AndroidManifest.xml").readText()
        assertTrue("no <activity>", !manifest.contains("<activity"))
        // Still a valid, parseable android app.
        assertEquals(ModuleType.ANDROID_APP, appModule(dir).type)
    }

    @Test
    fun `no androidx template disables androidx`() {
        val dir = generate(NewProjectSpec("Legacy", "com.example.legacy", "no-androidx"))

        assertTrue(File(dir, "gradle.properties").readText().contains("android.useAndroidX=false"))
        assertTrue(appModule(dir).dependencies.none { it.coordinate.startsWith("androidx") })
    }

    @Test
    fun `java option generates java sources and no kotlin plugin`() {
        val dir = generate(
            NewProjectSpec("Javaish", "com.example.javaish", "empty-views", language = TemplateLanguage.JAVA),
        )

        assertTrue(File(dir, "app/src/main/java/com/example/javaish/MainActivity.java").isFile)
        val appBuild = File(dir, "app/build.gradle.kts").readText()
        assertTrue("no kotlin plugin", !appBuild.contains("kotlin.android"))
        assertEquals(ModuleType.ANDROID_APP, appModule(dir).type)
    }

    @Test
    fun `groovy dsl option produces parseable groovy scripts`() {
        val dir = generate(
            NewProjectSpec("Groovyish", "com.example.groovyish", "empty-views", buildDsl = ProjectBuildDsl.GROOVY),
        )

        assertTrue(File(dir, "settings.gradle").isFile)
        assertTrue(File(dir, "app/build.gradle").isFile)
        assertTrue("no kts", !File(dir, "app/build.gradle.kts").exists())

        val result = reader.read(dir)
        assertTrue(result.diagnostics.none { it.severity == DiagnosticSeverity.ERROR })
        val app = result.model.modules.first { it.path == ":app" }
        assertEquals(ModuleType.ANDROID_APP, app.type)
        val android = BuildGradleParser.parse(File(dir, "app/build.gradle").readText(), GradleDsl.GROOVY).android
        assertEquals("com.example.groovyish", android!!.namespace)
    }

    @Test
    fun `cpp toggle augments a non-native template`() {
        val dir = generate(
            NewProjectSpec("Toggled", "com.example.toggled", "empty-views", useCpp = true),
        )
        assertTrue(File(dir, "app/src/main/cpp/CMakeLists.txt").isFile)
        assertTrue(File(dir, "app/build.gradle.kts").readText().contains("externalNativeBuild"))
    }
}
