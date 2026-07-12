package com.ahmadkharfan.androidstudiolite.data.gradle

import com.ahmadkharfan.androidstudiolite.data.gradle.parse.SettingsGradleParser
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsGradleParserTest {

    @Test
    fun ktsIncludeAndRootName() {
        val text = """
            rootProject.name = "MyApp"
            include(":app", ":core:common")
            include(":feature:home")
        """.trimIndent()
        val parsed = SettingsGradleParser.parse(text)
        assertEquals("MyApp", parsed.rootProjectName)
        assertEquals(listOf(":app", ":core:common", ":feature:home"), parsed.modulePaths)
    }

    @Test
    fun groovyIncludeWithoutParens() {
        val text = """
            rootProject.name = 'MyApp'
            include ':app', ':core'
            include ':data'
        """.trimIndent()
        val parsed = SettingsGradleParser.parse(text)
        assertEquals("MyApp", parsed.rootProjectName)
        assertEquals(listOf(":app", ":core", ":data"), parsed.modulePaths)
    }

    @Test
    fun projectDirOverrideCaptured() {
        val text = """
            include(":build:common")
            project(":build:common").projectDir = file("buildsystem/common")
        """.trimIndent()
        val parsed = SettingsGradleParser.parse(text)
        assertEquals(listOf(":build:common"), parsed.modulePaths)
        assertEquals("buildsystem/common", parsed.projectDirOverrides[":build:common"])
    }

    @Test
    fun commentsAndPluginManagementDoNotLeak() {
        val text = """
            pluginManagement {
                repositories { google(); mavenCentral() }
            }
            // include ":ignored"
            rootProject.name = "X"
            include(":app")
        """.trimIndent()
        val parsed = SettingsGradleParser.parse(text)
        assertEquals(listOf(":app"), parsed.modulePaths)
    }
}
