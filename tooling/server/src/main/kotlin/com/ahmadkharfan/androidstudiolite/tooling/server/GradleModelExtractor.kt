package com.ahmadkharfan.androidstudiolite.tooling.server

import com.ahmadkharfan.androidstudiolite.tooling.proto.DependencyDto
import com.ahmadkharfan.androidstudiolite.tooling.proto.Events
import com.ahmadkharfan.androidstudiolite.tooling.proto.ModuleDto
import com.ahmadkharfan.androidstudiolite.tooling.proto.Notification
import com.ahmadkharfan.androidstudiolite.tooling.proto.ProjectDto
import com.ahmadkharfan.androidstudiolite.tooling.proto.SourceSetDto
import com.ahmadkharfan.androidstudiolite.tooling.proto.SyncParams
import com.ahmadkharfan.androidstudiolite.tooling.proto.SyncResult
import com.ahmadkharfan.androidstudiolite.tooling.proto.VariantDto
import java.io.File
import org.gradle.tooling.CancellationToken
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject
import org.gradle.tooling.model.idea.IdeaModule
import org.gradle.tooling.model.idea.IdeaModuleDependency
import org.gradle.tooling.model.idea.IdeaProject
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency

/**
 * Turns a [ProjectConnection] into the shared [ProjectDto], using only Apache-2.0 Tooling-API models:
 *  - [GradleProject] gives the module tree (path, name, directory) for every project.
 *  - [IdeaProject] gives generic source directories and resolved dependencies (works for any JVM
 *    project without AGP).
 *  - [AndroidModelDump] fills in Android-only structure (build types, flavors, named source sets)
 *    via an init script when an `android` extension is present.
 */
class GradleModelExtractor(private val emit: (Notification) -> Unit) {

    fun extract(connection: ProjectConnection, params: SyncParams, token: CancellationToken): SyncResult {
        val dumpDir = File(System.getProperty("java.io.tmpdir"), "asl-android-model-${System.nanoTime()}")
        val dump = AndroidModelDump(dumpDir)
        val initScript = dump.writeInitScript(File(dumpDir, "asl-android-init.gradle"))

        try {
            emit(Events.progress("Fetching project structure"))
            val gradleProject = connection.model(GradleProject::class.java)
                .withCancellationToken(token)
                .apply {
                    params.javaHome?.let { setJavaHome(File(it)) }
                    if (params.arguments.isNotEmpty()) addArguments(params.arguments)
                    addArguments("--init-script", initScript.absolutePath)
                    setStandardOutput(LineForwardingOutputStream("stdout", emit))
                    setStandardError(LineForwardingOutputStream("stderr", emit))
                }
                .get()

            emit(Events.progress("Resolving dependencies"))
            // IdeaProject is best-effort: some projects (or Gradle versions) may fail to build it; the
            // generic module tree still yields a usable model.
            val ideaProject: IdeaProject? = runCatching {
                connection.model(IdeaProject::class.java)
                    .withCancellationToken(token)
                    .apply { params.javaHome?.let { setJavaHome(File(it)) } }
                    .get()
            }.getOrNull()

            val androidInfo = dump.readAll()
            val ideaByDir = ideaProject?.modules
                ?.associateBy { canonical(it.gradleProject.projectDirectory) }
                .orEmpty()

            val modules = flatten(gradleProject).map { gp ->
                val idea = ideaByDir[canonical(gp.projectDirectory)]
                val android = androidInfo[gp.path]
                ModuleDto(
                    path = gp.path,
                    name = gp.name,
                    type = moduleType(android, idea),
                    moduleDir = gp.projectDirectory.absolutePath,
                    variants = variantsOf(android),
                    sourceSets = sourceSetsOf(android, idea),
                    dependencies = dependenciesOf(idea),
                )
            }

            val root = rootOf(gradleProject)
            return SyncResult(
                ProjectDto(name = root.name, rootDir = root.projectDirectory.absolutePath, modules = modules),
            )
        } finally {
            dumpDir.deleteRecursively()
        }
    }

    // ------------------------------------------------------------------ tree

    private fun flatten(root: GradleProject): List<GradleProject> {
        val out = ArrayList<GradleProject>()
        fun visit(p: GradleProject) { out += p; p.children.forEach(::visit) }
        visit(root)
        return out
    }

    private fun rootOf(project: GradleProject): GradleProject {
        var p = project
        while (p.parent != null) p = p.parent!!
        return p
    }

    private fun canonical(file: File): String = runCatching { file.canonicalPath }.getOrDefault(file.absolutePath)

    // ------------------------------------------------------------------ per-module mapping

    private fun moduleType(android: AndroidModelDump.AndroidModuleInfo?, idea: IdeaModule?): String = when {
        android != null -> if (android.isApplication) "ANDROID_APP" else "ANDROID_LIBRARY"
        idea != null && idea.contentRoots.any { it.sourceDirectories.isNotEmpty() } -> "JVM"
        else -> "UNKNOWN"
    }

    private fun variantsOf(android: AndroidModelDump.AndroidModuleInfo?): List<VariantDto> {
        if (android == null) return emptyList()
        val buildTypes = android.buildTypes.ifEmpty { listOf("debug", "release") }
        if (android.flavors.isEmpty()) return buildTypes.map { VariantDto(it, it) }
        return android.flavors.flatMap { flavor ->
            buildTypes.map { bt ->
                VariantDto(flavor + bt.replaceFirstChar { it.uppercase() }, bt, listOf(flavor))
            }
        }
    }

    private fun sourceSetsOf(android: AndroidModelDump.AndroidModuleInfo?, idea: IdeaModule?): List<SourceSetDto> {
        // Android modules carry their own named source sets (main/debug/release/flavor/…).
        if (android != null && android.sourceSets.isNotEmpty()) return android.sourceSets

        if (idea == null) return emptyList()
        val main = ArrayList<File>()
        val test = ArrayList<File>()
        for (root in idea.contentRoots) {
            root.sourceDirectories.forEach { main += it.directory }
            root.testDirectories.forEach { test += it.directory }
        }
        val out = ArrayList<SourceSetDto>()
        if (main.isNotEmpty()) out += SourceSetDto(name = "main", javaDirs = main.map { it.absolutePath })
        if (test.isNotEmpty()) out += SourceSetDto(name = "test", javaDirs = test.map { it.absolutePath })
        return out
    }

    private fun dependenciesOf(idea: IdeaModule?): List<DependencyDto> {
        if (idea == null) return emptyList()
        val out = ArrayList<DependencyDto>()
        for (dep in idea.dependencies) {
            when (dep) {
                is IdeaSingleEntryLibraryDependency -> {
                    val gav = dep.gradleModuleVersion
                    val coordinate = if (gav != null) "${gav.group}:${gav.name}:${gav.version}"
                        else dep.file?.name ?: continue
                    out += DependencyDto(coordinate, scopeOf(dep.scope?.scope), dep.file?.absolutePath)
                }
                is IdeaModuleDependency -> {
                    @Suppress("DEPRECATION")
                    val target = dep.targetModuleName
                    out += DependencyDto(coordinate = ":$target", scope = scopeOf(dep.scope?.scope))
                }
            }
        }
        return out
    }

    /** Idea scopes are coarse ("COMPILE"/"PROVIDED"/"RUNTIME"/"TEST"); map to the shared scope names. */
    private fun scopeOf(ideaScope: String?): String = when (ideaScope?.uppercase()) {
        "COMPILE" -> "IMPLEMENTATION"
        "PROVIDED" -> "COMPILE_ONLY"
        "RUNTIME" -> "RUNTIME_ONLY"
        "TEST" -> "TEST"
        else -> "UNKNOWN"
    }
}
