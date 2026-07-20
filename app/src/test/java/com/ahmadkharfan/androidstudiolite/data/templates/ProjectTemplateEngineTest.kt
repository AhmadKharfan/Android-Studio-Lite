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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class ProjectTemplateEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()


    private val wrapperSource = GradleWrapperSource { path -> "fake:$path".byteInputStream() }
    private val engine = ProjectTemplateEngine(wrapperSource = wrapperSource)
    private val reader = GradleProjectReader()

    private fun generate(spec: NewProjectSpec): File {
        val dir = tmp.newFolder(spec.name.lowercase().replace(' ', '-') + "-" + spec.templateId)
        engine.generate(spec, dir)
        return dir
    }

    private fun appModule(dir: File): ModuleModel {
        val result = reader.read(dir)

        assertTrue(
            "unexpected parse errors: ${result.diagnostics.filter { it.severity == DiagnosticSeverity.ERROR }}",
            result.diagnostics.none { it.severity == DiagnosticSeverity.ERROR },
        )


        assertEquals(Catalog.GRADLE_VERSION, result.gradleVersion)
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


            assertTrue("${template.metadata.id}: settings", File(dir, "settings.gradle.kts").isFile)
            assertTrue("${template.metadata.id}: root build", File(dir, "build.gradle.kts").isFile)
            assertTrue("${template.metadata.id}: app build", File(dir, "app/build.gradle.kts").isFile)
            assertTrue("${template.metadata.id}: catalog", File(dir, "gradle/libs.versions.toml").isFile)
            assertTrue("${template.metadata.id}: wrapper", File(dir, "gradle/wrapper/gradle-wrapper.properties").isFile)
            assertTrue("${template.metadata.id}: manifest", File(dir, "app/src/main/AndroidManifest.xml").isFile)


            val gradlew = File(dir, "gradlew")
            assertTrue("${template.metadata.id}: gradlew", gradlew.isFile)
            assertTrue("${template.metadata.id}: gradlew executable", gradlew.canExecute())
            assertTrue("${template.metadata.id}: gradlew.bat", File(dir, "gradlew.bat").isFile)
            assertTrue(
                "${template.metadata.id}: wrapper jar",
                File(dir, "gradle/wrapper/gradle-wrapper.jar").isFile,
            )


            for (icon in listOf(
                "app/src/main/res/mipmap/ic_launcher.xml",
                "app/src/main/res/mipmap/ic_launcher_round.xml",
                "app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml",
                "app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml",
                "app/src/main/res/drawable/ic_launcher_foreground.xml",
                "app/src/main/res/values/ic_launcher_background.xml",
            )) {
                assertTrue("${template.metadata.id}: $icon", File(dir, icon).isFile)
            }


            val app = appModule(dir)
            assertEquals("${template.metadata.id}: type", ModuleType.ANDROID_APP, app.type)
            assertTrue("${template.metadata.id}: has variants", app.variants.isNotEmpty())
            assertTrue("${template.metadata.id}: has dependencies", app.dependencies.isNotEmpty())


            val android = BuildGradleParser.parse(File(dir, "app/build.gradle.kts").readText(), GradleDsl.KOTLIN).android
            assertNotNull("${template.metadata.id}: android block", android)
            assertEquals("${template.metadata.id}: namespace", "com.example.sample", android!!.namespace)
            assertEquals("${template.metadata.id}: minSdk", "24", android.minSdk)
            assertEquals("${template.metadata.id}: targetSdk", "34", android.targetSdk)
            assertEquals("${template.metadata.id}: compileSdk", "34", android.compileSdk)
        }
    }

    @Test
    fun `the gradle wrapper binaries ship as app assets`() {
        for (path in GradleWrapperSource.PATHS) {
            val asset = File("src/main/assets/wrapper", path)
            assertTrue("missing asset: ${asset.path}", asset.isFile)
            assertTrue("empty asset: ${asset.path}", asset.length() > 0)
        }
    }

    @Test
    fun `views themes do not reference material2 attributes`() {
        val dir = generate(NewProjectSpec("Viewsy", "com.example.viewsy", "empty-views"))

        val themes = File(dir, "app/src/main/res/values/themes.xml").readText()
        assertTrue("M3 parent", themes.contains("Theme.Material3.DayNight.NoActionBar"))
        assertTrue("no colorPrimaryVariant", !themes.contains("colorPrimaryVariant"))
    }

    @Test
    fun `empty compose template wires compose deps and a theme package`() {
        val dir = generate(NewProjectSpec("Composer", "com.example.composer", "empty-compose"))

        val app = appModule(dir)
        assertTrue(app.dependencies.any { it.coordinate == "androidx.core:core-ktx:1.13.1" })
        assertTrue(app.dependencies.any { it.coordinate == "androidx.compose:compose-bom:${Catalog.composeBom.version}" })

        assertTrue(app.dependencies.any { it.coordinate == "androidx.compose.material3:material3" })

        val appBuild = File(dir, "app/build.gradle.kts").readText()
        assertTrue("compose enabled", appBuild.contains("compose = true"))


        assertTrue("compose compiler plugin applied", appBuild.contains("alias(libs.plugins.compose.compiler)"))
        assertFalse("Kotlin 1.9 composeOptions must be gone", appBuild.contains("composeOptions"))

        val catalog = File(dir, "gradle/libs.versions.toml").readText()
        assertTrue(
            "compose plugin in catalog",
            catalog.contains("compose-compiler = { id = \"org.jetbrains.kotlin.plugin.compose\""),
        )


        assertTrue("compose plugin tracks kotlin version", catalog.contains("kotlin = \"${Catalog.KOTLIN_VERSION}\""))

        assertTrue(File(dir, "app/src/main/java/com/example/composer/MainActivity.kt").isFile)
        assertTrue(File(dir, "app/src/main/java/com/example/composer/ui/theme/Theme.kt").isFile)
        assertTrue(File(dir, "app/src/main/java/com/example/composer/ui/theme/Color.kt").isFile)
    }

    @Test
    fun `nav templates get their NavController from the host fragment, not the activity`() {
        for (templateId in listOf("bottom-nav", "nav-drawer")) {
            val dir = generate(NewProjectSpec("Navvy", "com.example.navvy", templateId))
            val main = File(dir, "app/src/main/java/com/example/navvy/MainActivity.kt").readText()

            assertTrue("$templateId: uses NavHostFragment", main.contains("as NavHostFragment"))
            assertTrue(
                "$templateId: must not call Activity.findNavController",
                !main.contains("findNavController(R.id."),
            )
        }
    }

    @Test
    fun `nav drawer wires a toolbar to the nav controller`() {
        val dir = generate(NewProjectSpec("Drawy", "com.example.drawy", "nav-drawer"))

        val layout = File(dir, "app/src/main/res/layout/activity_main.xml").readText()
        assertTrue("toolbar in layout", layout.contains("androidx.appcompat.widget.Toolbar"))
        val main = File(dir, "app/src/main/java/com/example/drawy/MainActivity.kt").readText()
        assertTrue("action bar set", main.contains("setSupportActionBar"))
        assertTrue("action bar wired to nav", main.contains("setupActionBarWithNavController"))
    }

    @Test
    fun `no activity template omits the launcher activity`() {
        val dir = generate(NewProjectSpec("Empty", "com.example.empty", "no-activity"))

        val manifest = File(dir, "app/src/main/AndroidManifest.xml").readText()
        assertTrue("no <activity>", !manifest.contains("<activity"))

        assertEquals(ModuleType.ANDROID_APP, appModule(dir).type)
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
}
