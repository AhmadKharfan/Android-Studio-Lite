package com.ahmadkharfan.androidstudiolite.data.gradle

import com.ahmadkharfan.androidstudiolite.data.gradle.model.ParsedPlugin
import com.ahmadkharfan.androidstudiolite.data.gradle.parse.VersionCatalogParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AgpVersionResolverTest {

    private fun catalog(toml: String) = VersionCatalogParser.parse(toml.trimIndent())

    @Test
    fun resolvesFromPluginsTableWithVersionRef() {
        val c = catalog(
            """
            [versions]
            android_gradle_plugin = "9.2.1"
            [plugins]
            android_application = { id = "com.android.application", version.ref = "android_gradle_plugin" }
            """,
        )
        assertEquals("9.2.1", AgpVersionResolver.resolve(c))
    }

    @Test
    fun resolvesFromLibrariesTableGradleArtifact() {
        val c = catalog(
            """
            [versions]
            agpVer = "8.5.0"
            [libraries]
            android-gradle = { module = "com.android.tools.build:gradle", version.ref = "agpVer" }
            """,
        )
        assertEquals("8.5.0", AgpVersionResolver.resolve(c))
    }

    @Test
    fun resolvesFromVersionsWithNonStandardKeyName() {
        val c = catalog(
            """
            [versions]
            android_gradle_plugin = "8.6.1"
            """,
        )
        assertEquals("8.6.1", AgpVersionResolver.resolve(c))
    }

    @Test
    fun resolvesFromShortAgpKey() {
        val c = catalog(
            """
            [versions]
            agp = "8.7.2"
            """,
        )
        assertEquals("8.7.2", AgpVersionResolver.resolve(c))
    }

    @Test
    fun resolvesFromModuleInlinePluginVersionWhenNoCatalog() {
        val plugins = listOf(ParsedPlugin(id = "com.android.application", version = "8.4.0"))
        assertEquals("8.4.0", AgpVersionResolver.resolve(catalog = null, modulePlugins = plugins))
    }

    @Test
    fun returnsNullWhenNoAgpDeclared() {
        val c = catalog(
            """
            [versions]
            coreKtx = "1.13.0"
            [libraries]
            core = { module = "androidx.core:core-ktx", version.ref = "coreKtx" }
            """,
        )
        assertNull(AgpVersionResolver.resolve(c))
    }
}
