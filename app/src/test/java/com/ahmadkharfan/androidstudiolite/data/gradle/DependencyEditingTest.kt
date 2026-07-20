package com.ahmadkharfan.androidstudiolite.data.gradle

import com.ahmadkharfan.androidstudiolite.data.gradle.deps.DependenciesBlockEditor
import com.ahmadkharfan.androidstudiolite.data.gradle.deps.VersionCatalogEditor
import com.ahmadkharfan.androidstudiolite.data.gradle.model.GradleDsl
import com.ahmadkharfan.androidstudiolite.data.gradle.parse.BuildGradleParser
import com.ahmadkharfan.androidstudiolite.data.gradle.parse.VersionCatalogParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DependencyEditingTest {


    private val catalog = """
        [versions]
        coreKtx = "1.12.0"

        [libraries]
        androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }

        [plugins]
        android-application = { id = "com.android.application" }
    """.trimIndent()

    @Test
    fun addLibraryInsertsUnderLibrariesTable() {
        val result = VersionCatalogEditor.addLibrary(catalog, "retrofit", "com.squareup.retrofit2", "retrofit", "2.11.0")
        result as VersionCatalogEditor.Result.Changed

        val parsed = VersionCatalogParser.parse(result.text)
        assertEquals("com.squareup.retrofit2:retrofit:2.11.0", parsed.findLibrary("retrofit")?.coordinate)

        assertEquals("androidx.core:core-ktx:1.12.0", parsed.findLibrary("androidx.core.ktx")?.coordinate)
        assertEquals("com.android.application", parsed.findPlugin("android.application")?.id)
    }

    @Test
    fun addLibraryIsIdempotentByAlias() {
        val r = VersionCatalogEditor.addLibrary(catalog, "androidx-core-ktx", "x", "y", "1")
        assertTrue(r is VersionCatalogEditor.Result.Unchanged)
    }

    @Test
    fun removeLibraryDropsEntry() {
        val r = VersionCatalogEditor.removeLibrary(catalog, "androidx-core-ktx")
        r as VersionCatalogEditor.Result.Changed
        assertTrue(VersionCatalogParser.parse(r.text).findLibrary("androidx.core.ktx") == null)
    }

    @Test
    fun addLibraryCreatesTableWhenMissing() {
        val r = VersionCatalogEditor.addLibrary("[versions]\nfoo = \"1\"\n", "okhttp", "com.squareup.okhttp3", "okhttp", "4.12.0")
        r as VersionCatalogEditor.Result.Changed
        assertEquals("com.squareup.okhttp3:okhttp:4.12.0", VersionCatalogParser.parse(r.text).findLibrary("okhttp")?.coordinate)
    }


    @Test
    fun addToKtsDependenciesBlock() {
        val build = """
            plugins { id("com.android.application") }

            dependencies {
                implementation("androidx.core:core-ktx:1.12.0")
            }
        """.trimIndent()
        val r = DependenciesBlockEditor.add(build, "implementation", "junit:junit:4.13.2", GradleDsl.KOTLIN, quoteNotation = true)
        r as DependenciesBlockEditor.Result.Changed
        assertTrue(r.text.contains("implementation(\"junit:junit:4.13.2\")"))

        val deps = BuildGradleParser.parse(r.text, GradleDsl.KOTLIN).dependencies.mapNotNull { it.coordinate }
        assertTrue(deps.contains("androidx.core:core-ktx:1.12.0"))
        assertTrue(deps.contains("junit:junit:4.13.2"))
    }

    @Test
    fun addToGroovyDependenciesBlockUsesGroovySyntax() {
        val build = "dependencies {\n    implementation 'androidx.core:core-ktx:1.12.0'\n}"
        val r = DependenciesBlockEditor.add(build, "implementation", "junit:junit:4.13.2", GradleDsl.GROOVY, quoteNotation = true)
        r as DependenciesBlockEditor.Result.Changed
        assertTrue(r.text.contains("implementation \"junit:junit:4.13.2\""))
    }

    @Test
    fun addCatalogAccessorUnquoted() {
        val build = "dependencies {\n    implementation(libs.core)\n}"
        val r = DependenciesBlockEditor.add(build, "implementation", "libs.retrofit", GradleDsl.KOTLIN, quoteNotation = false)
        r as DependenciesBlockEditor.Result.Changed
        assertTrue(r.text.contains("implementation(libs.retrofit)"))
    }

    @Test
    fun addIsIdempotent() {
        val build = "dependencies {\n    implementation(\"junit:junit:4.13.2\")\n}"
        val r = DependenciesBlockEditor.add(build, "implementation", "junit:junit:4.13.2", GradleDsl.KOTLIN, quoteNotation = true)
        assertTrue(r is DependenciesBlockEditor.Result.Unchanged)
    }

    @Test
    fun addCreatesBlockWhenMissing() {
        val build = "plugins { id(\"com.android.library\") }\n"
        val r = DependenciesBlockEditor.add(build, "implementation", "junit:junit:4.13.2", GradleDsl.KOTLIN, quoteNotation = true)
        r as DependenciesBlockEditor.Result.Changed
        assertTrue(r.text.contains("dependencies {"))
        assertTrue(BuildGradleParser.parse(r.text, GradleDsl.KOTLIN).dependencies.any { it.coordinate == "junit:junit:4.13.2" })
    }

    @Test
    fun removeDependencyLine() {
        val build = "dependencies {\n    implementation(\"junit:junit:4.13.2\")\n    api(\"a:b:1.0\")\n}"
        val r = DependenciesBlockEditor.remove(build, "junit:junit")
        r as DependenciesBlockEditor.Result.Changed
        assertTrue(!r.text.contains("junit:junit"))
        assertTrue(r.text.contains("a:b:1.0"))
    }
}
