package com.ahmadkharfan.androidstudiolite.data.gradle

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.DependencyScope
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class GradleProjectReaderTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val reader = GradleProjectReader()

    private fun sampleProject(): File {
        val root = tmp.newFolder("sample")
        write(root, "settings.gradle.kts", """
            rootProject.name = "Sample"
            include(":app", ":core")
        """.trimIndent())
        write(root, "gradle.properties", "android.useAndroidX=true\n")
        write(root, "gradle/wrapper/gradle-wrapper.properties",
            "distributionUrl=https\\://services.gradle.org/distributions/gradle-8.7-bin.zip\n")
        write(root, "gradle/libs.versions.toml", """
            [versions]
            coreKtx = "1.12.0"
            [libraries]
            androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
            [plugins]
            android-application = { id = "com.android.application" }
        """.trimIndent())

        write(root, "app/build.gradle.kts", """
            plugins {
                id("com.android.application")
                kotlin("android")
            }
            android {
                namespace = "com.example.app"
                compileSdk = 34
                defaultConfig {
                    applicationId = "com.example.app"
                    minSdk = 24
                    targetSdk = 34
                }
                buildTypes { debug { }; release { } }
            }
            dependencies {
                implementation(libs.androidx.core.ktx)
                implementation(project(":core"))
                testImplementation("junit:junit:4.13.2")
            }
        """.trimIndent())
        write(root, "app/src/main/AndroidManifest.xml",
            "<manifest xmlns:android=\"http://schemas.android.com/apk/res/android\"></manifest>")

        write(root, "app/src/main/java/com/example/app/Main.kt", "package com.example.app\n")

        write(root, "core/build.gradle.kts", """
            plugins { id("com.android.library") }
            android { namespace = "com.example.core"; compileSdk = 34 }
            dependencies { api("com.google.code.gson:gson:2.10.1") }
        """.trimIndent())
        return root
    }

    @Test
    fun readsModulesTypesAndSdk() {
        val result = reader.read(sampleProject())
        assertEquals("Sample", result.model.name)
        assertEquals("8.7", result.gradleVersion)

        val app = result.model.modules.first { it.path == ":app" }
        assertEquals(ModuleType.ANDROID_APP, app.type)

        val core = result.model.modules.first { it.path == ":core" }
        assertEquals(ModuleType.ANDROID_LIBRARY, core.type)
    }

    @Test
    fun resolvesCatalogAndProjectAndPlainDependencies() {
        val app = reader.read(sampleProject()).model.modules.first { it.path == ":app" }
        val coords = app.dependencies.map { it.coordinate }

        assertTrue(coords.contains("androidx.core:core-ktx:1.12.0"))

        assertTrue(coords.contains(":core"))

        val junit = app.dependencies.first { it.coordinate == "junit:junit:4.13.2" }
        assertEquals(DependencyScope.TEST, junit.scope)
    }

    @Test
    fun computesVariantsFromBuildTypes() {
        val app = reader.read(sampleProject()).model.modules.first { it.path == ":app" }
        assertEquals(setOf("debug", "release"), app.variants.map { it.name }.toSet())
    }

    @Test
    fun `getByName release still yields debug variants like Android Studio`() {


        val root = tmp.newFolder("flavored")
        write(root, "settings.gradle.kts", """
            rootProject.name = "Flavored"
            include(":composeApp")
        """.trimIndent())
        write(root, "composeApp/build.gradle.kts", """
            plugins { id("com.android.application") }
            android {
                namespace = "com.example.mena"
                compileSdk = 34
                defaultConfig { applicationId = "com.example.mena"; minSdk = 24 }
                buildTypes {
                    getByName("release") { isMinifyEnabled = false }
                }
                flavorDimensions += "environment"
                productFlavors {
                    create("development") { dimension = "environment" }
                    create("staging") { dimension = "environment" }
                    create("production") { dimension = "environment" }
                }
            }
        """.trimIndent())

        val app = reader.read(root).model.modules.first { it.path == ":composeApp" }
        val names = app.variants.map { it.name }.toSet()
        assertTrue(names.contains("developmentDebug"))
        assertTrue(names.contains("developmentRelease"))
        assertTrue(names.contains("stagingDebug"))
        assertTrue(names.contains("productionDebug"))


    }

    @Test
    fun combinesMultipleFlavorDimensionsInDeclaredOrder() {
        val root = tmp.newFolder("multidim")
        write(root, "settings.gradle.kts", """
            rootProject.name = "MultiDim"
            include(":app")
        """.trimIndent())
        write(root, "app/build.gradle.kts", """
            plugins { id("com.android.application") }
            android {
                namespace = "com.example.multi"
                defaultConfig { applicationId = "com.example.multi" }
                flavorDimensions += listOf("tier", "env")
                productFlavors {
                    create("free") { dimension = "tier" }
                    create("paid") { dimension = "tier" }
                    create("prod") { dimension = "env" }
                    create("staging") { dimension = "env" }
                }
            }
        """.trimIndent())

        val app = reader.read(root).model.modules.first { it.path == ":app" }
        val names = app.variants.map { it.name }.toSet()
        assertTrue("expected freeProdDebug in $names", names.contains("freeProdDebug"))
        assertTrue(names.contains("paidStagingRelease"))
        assertTrue(names.contains("freeStagingDebug"))
        assertTrue(names.contains("paidProdRelease"))
        assertTrue(names.none { it.startsWith("prod") || it.startsWith("staging") })

        val freeProdDebug = app.variants.first { it.name == "freeProdDebug" }
        assertEquals(":app:assembleFreeProdDebug", freeProdDebug.assembleTaskPath)
        assertEquals(listOf("free", "prod"), freeProdDebug.flavors)
    }

    @Test
    fun detectsAppAppliedViaConventionPluginWithNonAppModuleName() {
        val root = tmp.newFolder("conv")
        write(root, "settings.gradle.kts", """
            rootProject.name = "Conv"
            include(":mobile")
        """.trimIndent())
        write(root, "mobile/build.gradle.kts", """
            plugins { id("io.acme.android-application") }
            android {
                namespace = "com.acme.mobile"
                defaultConfig { applicationId = "com.acme.mobile" }
                buildTypes { debug { }; release { } }
            }
        """.trimIndent())

        val mobile = reader.read(root).model.modules.first { it.path == ":mobile" }
        assertEquals(ModuleType.ANDROID_APP, mobile.type)
        assertEquals(setOf("debug", "release"), mobile.variants.map { it.name }.toSet())
    }

    @Test
    fun effectiveBuildTypesAlwaysIncludesDebugAndRelease() {
        assertEquals(listOf("debug", "release"), reader.effectiveBuildTypes(emptyList()))
        assertEquals(listOf("debug", "release"), reader.effectiveBuildTypes(listOf("release")))
        assertEquals(
            listOf("debug", "release", "benchmark"),
            reader.effectiveBuildTypes(listOf("benchmark", "release")),
        )
    }

    @Test
    fun mainSourceSetHasConventionalDirs() {
        val app = reader.read(sampleProject()).model.modules.first { it.path == ":app" }
        val main = app.sourceSets.first { it.name == "main" }
        assertNotNull(main.manifestFile)
        assertTrue(main.javaDirs.any { it.path.endsWith("src/main/java") })
    }

    @Test
    fun unresolvedCatalogRefProducesDiagnostic() {
        val root = tmp.newFolder("bad")
        write(root, "settings.gradle.kts", "include(\":app\")")
        write(root, "gradle/libs.versions.toml", "[libraries]\n")
        write(root, "app/build.gradle.kts", """
            plugins { id("com.android.application") }
            dependencies { implementation(libs.does.not.exist) }
        """.trimIndent())
        val result = reader.read(root)
        assertTrue(result.diagnostics.any { it.code == "gradle.unresolvedCatalogRef" })
    }

    @Test
    fun toleratesModuleWithoutBuildScript() {
        val root = tmp.newFolder("partial")
        write(root, "settings.gradle.kts", "include(\":ghost\")")
        val result = reader.read(root)
        val ghost = result.model.modules.first { it.path == ":ghost" }
        assertEquals(ModuleType.UNKNOWN, ghost.type)
        assertTrue(result.diagnostics.any { it.code == "gradle.noBuildScript" })
    }

    private fun write(root: File, relPath: String, content: String) {
        val file = File(root, relPath)
        file.parentFile?.mkdirs()
        file.writeText(content)
    }
}
