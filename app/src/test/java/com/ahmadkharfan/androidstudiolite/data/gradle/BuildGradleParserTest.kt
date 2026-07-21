package com.ahmadkharfan.androidstudiolite.data.gradle

import com.ahmadkharfan.androidstudiolite.data.gradle.model.GradleDsl
import com.ahmadkharfan.androidstudiolite.data.gradle.model.RawDependencyKind
import com.ahmadkharfan.androidstudiolite.data.gradle.parse.BuildGradleParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildGradleParserTest {

    @Test
    fun ktsPluginsBlock() {
        val text = """
            plugins {
                id("com.android.application")
                kotlin("android")
                alias(libs.plugins.kotlin.compose)
                id("com.google.devtools.ksp") version "2.0.0-1.0.21"
            }
        """.trimIndent()
        val parsed = BuildGradleParser.parse(text, GradleDsl.KOTLIN)
        val ids = parsed.plugins.map { it.id }
        assertTrue(ids.contains("com.android.application"))
        assertTrue(ids.contains("org.jetbrains.kotlin.android"))
        assertTrue(parsed.plugins.any { it.fromCatalog && it.id == "kotlin.compose" })
        val ksp = parsed.plugins.first { it.id == "com.google.devtools.ksp" }
        assertEquals("2.0.0-1.0.21", ksp.version)
    }

    @Test
    fun groovyPluginsAndLegacyApply() {
        val text = """
            plugins {
                id 'com.android.library'
            }
            apply plugin: 'kotlin-android'
        """.trimIndent()
        val parsed = BuildGradleParser.parse(text, GradleDsl.GROOVY)
        val ids = parsed.plugins.map { it.id }
        assertTrue(ids.contains("com.android.library"))
        assertTrue(ids.contains("kotlin-android"))
    }

    @Test
    fun androidBlockKts() {
        val text = """
            android {
                namespace = "com.example.app"
                compileSdk = 34
                defaultConfig {
                    applicationId = "com.example.app"
                    minSdk = 24
                    targetSdk = 34
                    versionCode = 7
                    versionName = "1.2.3"
                }
                flavorDimensions += "tier"
                productFlavors {
                    create("free") { dimension = "tier" }
                    create("paid") { dimension = "tier" }
                }
                buildTypes {
                    debug { }
                    release { isMinifyEnabled = true }
                }
            }
        """.trimIndent()
        val a = BuildGradleParser.parse(text, GradleDsl.KOTLIN).android
        assertNotNull(a)
        requireNotNull(a)
        assertEquals("com.example.app", a.namespace)
        assertEquals("34", a.compileSdk)
        assertEquals("24", a.minSdk)
        assertEquals("34", a.targetSdk)
        assertEquals("7", a.versionCode)
        assertEquals("1.2.3", a.versionName)
        assertEquals(listOf("free", "paid"), a.productFlavors)
        assertEquals(listOf("debug", "release"), a.buildTypes)
        assertEquals(listOf("tier"), a.flavorDimensions)
        assertEquals(mapOf("free" to "tier", "paid" to "tier"), a.flavorDimensionOf)
    }

    @Test
    fun buildTypesGetByNameReleaseIsParsed() {
        val text = """
            android {
                buildTypes {
                    getByName("release") { isMinifyEnabled = false }
                }
            }
        """.trimIndent()
        val a = BuildGradleParser.parse(text, GradleDsl.KOTLIN).android
        requireNotNull(a)

        assertEquals(listOf("release"), a.buildTypes)
    }

    @Test
    fun androidBlockGroovyScalarSyntax() {
        val text = """
            android {
                namespace "com.example.groovy"
                compileSdkVersion 33
                defaultConfig {
                    minSdkVersion 21
                    targetSdkVersion 33
                }
            }
        """.trimIndent()
        val a = BuildGradleParser.parse(text, GradleDsl.GROOVY).android
        requireNotNull(a)
        assertEquals("com.example.groovy", a.namespace)
        assertEquals("33", a.compileSdk)
        assertEquals("21", a.minSdk)
        assertEquals("33", a.targetSdk)
    }

    @Test
    fun dependenciesKtsAllForms() {
        val text = """
            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
                api(libs.retrofit)
                implementation(project(":core"))
                implementation(platform("androidx.compose:compose-bom:2024.01.00"))
                testImplementation("junit:junit:4.13.2")
                implementation(kotlin("stdlib"))
                implementation(libs.bundles.networking)
            }
        """.trimIndent()
        val deps = BuildGradleParser.parse(text, GradleDsl.KOTLIN).dependencies

        val core = deps.first { it.coordinate == "androidx.core:core-ktx:1.12.0" }
        assertEquals("implementation", core.configuration)
        assertEquals(RawDependencyKind.MODULE, core.kind)

        val retrofit = deps.first { it.catalogAccessor == "libs.retrofit" }
        assertEquals(RawDependencyKind.CATALOG, retrofit.kind)
        assertEquals("api", retrofit.configuration)

        val proj = deps.first { it.kind == RawDependencyKind.PROJECT }
        assertEquals(":core", proj.coordinate)

        val bom = deps.first { it.isPlatform }
        assertEquals("androidx.compose:compose-bom:2024.01.00", bom.coordinate)

        assertTrue(deps.any { it.coordinate == "org.jetbrains.kotlin:kotlin-stdlib" })
        assertTrue(deps.any { it.kind == RawDependencyKind.CATALOG_BUNDLE && it.catalogAccessor == "libs.bundles.networking" })
    }

    @Test
    fun dependenciesGroovyForms() {
        val text = """
            dependencies {
                implementation 'androidx.core:core-ktx:1.12.0'
                testImplementation 'junit:junit:4.13.2'
                implementation project(':core')
            }
        """.trimIndent()
        val deps = BuildGradleParser.parse(text, GradleDsl.GROOVY).dependencies
        assertTrue(deps.any { it.coordinate == "androidx.core:core-ktx:1.12.0" && it.configuration == "implementation" })
        assertTrue(deps.any { it.coordinate == "junit:junit:4.13.2" && it.configuration == "testImplementation" })
        assertTrue(deps.any { it.kind == RawDependencyKind.PROJECT && it.coordinate == ":core" })
    }

    @Test
    fun tolerantOnMissingAndroidBlock() {
        val parsed = BuildGradleParser.parse("plugins { id(\"java-library\") }", GradleDsl.KOTLIN)
        assertEquals(null, parsed.android)
        assertTrue(parsed.dependencies.isEmpty())
    }
}
