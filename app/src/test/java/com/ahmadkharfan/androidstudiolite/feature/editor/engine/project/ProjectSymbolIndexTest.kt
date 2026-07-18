package com.ahmadkharfan.androidstudiolite.feature.editor.engine.project

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.DependencyModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.DependencyScope
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.SourceSetModel
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionKind
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorCompletionController
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorLanguage
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ProjectSymbolIndexTest {

    @get:Rule
    val tmp = TemporaryFolder()

    /** Build a ProjectModel whose one module has [sources] under src/main/kotlin and the given jar deps. */
    private fun fixtureModel(sources: Map<String, String>, jars: List<File> = emptyList()): ProjectModel {
        val moduleDir = tmp.newFolder("app")
        val kotlinRoot = File(moduleDir, "src/main/kotlin").apply { mkdirs() }
        for ((relPath, content) in sources) {
            val file = File(kotlinRoot, relPath)
            file.parentFile?.mkdirs()
            file.writeText(content)
        }
        val module = ModuleModel(
            path = ":app",
            name = "app",
            type = ModuleType.ANDROID_APP,
            moduleDir = moduleDir,
            sourceSets = listOf(SourceSetModel(name = "main", kotlinDirs = listOf(kotlinRoot))),
            dependencies = jars.map { DependencyModel("dep:${it.name}", DependencyScope.IMPLEMENTATION, it) },
        )
        return ProjectModel(name = "fixture", rootDir = tmp.root, modules = listOf(module))
    }

    /** Write a jar containing empty entries for the given class binary names (slash-separated). */
    private fun jarWithClasses(vararg classPaths: String): File {
        val jar = tmp.newFile("dep-${classPaths.hashCode()}.jar")
        ZipOutputStream(jar.outputStream()).use { zip ->
            for (path in classPaths) {
                zip.putNextEntry(ZipEntry("$path.class"))
                zip.write(byteArrayOf(0xCA.toByte(), 0xFE.toByte()))
                zip.closeEntry()
            }
        }
        return jar
    }

    @Test
    fun indexes_topLevel_declarations_from_project_sources() {
        val model = fixtureModel(
            mapOf(
                "com/example/Widgets.kt" to """
                    package com.example
                    class WidgetPanel
                    fun renderWidget() {}
                    val widgetCount = 3
                """.trimIndent(),
            ),
        )
        val index = ProjectSymbolIndexer.index(model)
        assertTrue("WidgetPanel" in index.simpleNames)
        assertTrue("renderWidget" in index.simpleNames)
        assertTrue("widgetCount" in index.simpleNames)

        val panel = index.symbols.first { it.simpleName == "WidgetPanel" }
        assertEquals("com.example.WidgetPanel", panel.qualifiedName)
        assertEquals(CompletionKind.Class, panel.kind)
        assertEquals(SymbolOrigin.PROJECT, panel.origin)
    }

    @Test
    fun indexes_public_classes_from_dependency_jars() {
        val jar = jarWithClasses(
            "com/squareup/okhttp3/OkHttpClient",
            "com/squareup/okhttp3/OkHttpClient\$Builder", // inner class, must be skipped
            "com/squareup/okhttp3/package-info", // pseudo-class, must be skipped
        )
        val index = ProjectSymbolIndexer.index(fixtureModel(emptyMap(), listOf(jar)))
        val okhttp = index.symbols.filter { it.origin == SymbolOrigin.DEPENDENCY }
        assertTrue("OkHttpClient" in okhttp.map { it.simpleName })
        assertFalse("Builder" in okhttp.map { it.simpleName })
        assertFalse(okhttp.any { it.simpleName == "package-info" })
        assertEquals("com.squareup.okhttp3.OkHttpClient", okhttp.first().qualifiedName)
    }

    @Test
    fun completion_offers_project_and_dependency_symbols_after_sync() {
        val jar = jarWithClasses("com/squareup/okhttp3/OkHttpClient")
        val model = fixtureModel(
            mapOf("com/example/Repo.kt" to "package com.example\nclass RepositoryHub"),
            listOf(jar),
        )
        val controller = EditorCompletionController()
        controller.projectIndex = ProjectSymbolIndexer.index(model)

        val session = EditorSession("val x = Reposi", EditorLanguage.Kotlin).also { it.setCaret(14) }
        val projectLabels = controller.queryHeuristic(session).map { it.label }
        assertTrue("RepositoryHub" in projectLabels)

        val depSession = EditorSession("val y = OkHttp", EditorLanguage.Kotlin).also { it.setCaret(14) }
        val depLabels = controller.queryHeuristic(depSession).map { it.label }
        assertTrue("OkHttpClient" in depLabels)
    }


    @Test
    fun memberAccess_offers_subpackages_and_classes_under_qualifier() {
        val jar = jarWithClasses("com/example/net/HttpClient", "com/example/net/dns/DnsResolver")
        val index = ProjectSymbolIndexer.index(fixtureModel(emptyMap(), listOf(jar)))
        val underNet = index.membersOf("com.example.net").map { it.label }
        assertTrue("HttpClient" in underNet)
        assertTrue("dns" in underNet) // sub-package surfaced
    }

    @Test
    fun empty_index_is_a_noop() {
        assertTrue(ProjectSymbolIndex.EMPTY.isEmpty())
        assertTrue(ProjectSymbolIndex.EMPTY.topLevelMatching("Foo").isEmpty())
    }
}
