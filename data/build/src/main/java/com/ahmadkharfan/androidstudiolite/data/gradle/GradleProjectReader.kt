package com.ahmadkharfan.androidstudiolite.data.gradle

import com.ahmadkharfan.androidstudiolite.data.gradle.model.CatalogLibrary
import com.ahmadkharfan.androidstudiolite.data.gradle.model.DiagnosticSeverity
import com.ahmadkharfan.androidstudiolite.data.gradle.model.GradleDiagnostic
import com.ahmadkharfan.androidstudiolite.data.gradle.model.GradleDsl
import com.ahmadkharfan.androidstudiolite.data.gradle.model.ParsedAndroidBlock
import com.ahmadkharfan.androidstudiolite.data.gradle.model.ParsedBuildScript
import com.ahmadkharfan.androidstudiolite.data.gradle.model.RawDependency
import com.ahmadkharfan.androidstudiolite.data.gradle.model.RawDependencyKind
import com.ahmadkharfan.androidstudiolite.data.gradle.model.VersionCatalog
import com.ahmadkharfan.androidstudiolite.data.gradle.parse.BuildGradleParser
import com.ahmadkharfan.androidstudiolite.data.gradle.parse.GradlePropertiesParser
import com.ahmadkharfan.androidstudiolite.data.gradle.parse.SettingsGradleParser
import com.ahmadkharfan.androidstudiolite.data.gradle.parse.VersionCatalogParser
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.DependencyModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.DependencyScope
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.SourceSetModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.VariantModel
import java.io.File

data class GradleProjectReadResult(
    val model: ProjectModel,
    val diagnostics: List<GradleDiagnostic>,
    val gradleVersion: String?,
    val catalog: VersionCatalog?,
    val gradleProperties: Map<String, String>,
)

class GradleProjectReader {

    fun isGradleProject(dir: File): Boolean =
        File(dir, "settings.gradle.kts").isFile || File(dir, "settings.gradle").isFile

    fun read(projectRoot: File): GradleProjectReadResult {
        val diagnostics = ArrayList<GradleDiagnostic>()

        val catalog = readCatalog(projectRoot, diagnostics)
        val gradleProps = readProperties(File(projectRoot, "gradle.properties"))
        val gradleVersion = readGradleVersion(projectRoot, diagnostics)

        val settingsFile = firstExisting(projectRoot, "settings.gradle.kts", "settings.gradle")
        val settings = settingsFile?.let { SettingsGradleParser.parse(it.readText()) }
        if (settings == null) {
            diagnostics += GradleDiagnostic(
                "No settings.gradle(.kts) found; treating the root directory as a single module.",
                "gradle.noSettings", DiagnosticSeverity.INFO,
            )
        }

        val rootName = settings?.rootProjectName ?: projectRoot.name


        val moduleDirs = LinkedHashMap<String, File>()
        if (settings != null) {
            for (path in settings.modulePaths) {
                moduleDirs[path] = resolveModuleDir(projectRoot, path, settings.projectDirOverrides)
            }
        }

        if (firstExisting(projectRoot, "build.gradle.kts", "build.gradle") != null) {
            moduleDirs.putIfAbsent(":", projectRoot)
        }
        if (moduleDirs.isEmpty()) {
            diagnostics += GradleDiagnostic(
                "No modules found (no included projects and no root build script).",
                "gradle.noModules", DiagnosticSeverity.WARNING,
            )
        }

        val modules = moduleDirs.map { (path, dir) ->
            readModule(path, dir, projectRoot, catalog, diagnostics)
        }

        return GradleProjectReadResult(
            model = ProjectModel(name = rootName, rootDir = projectRoot, modules = modules),
            diagnostics = diagnostics,
            gradleVersion = gradleVersion,
            catalog = catalog,
            gradleProperties = gradleProps,
        )
    }


    private fun readModule(
        path: String,
        dir: File,
        projectRoot: File,
        catalog: VersionCatalog?,
        diagnostics: MutableList<GradleDiagnostic>,
    ): ModuleModel {
        val name = if (path == ":") projectRoot.name else path.trimStart(':').substringAfterLast(':')
        val buildFile = firstExisting(dir, "build.gradle.kts", "build.gradle")
        if (buildFile == null) {
            diagnostics += GradleDiagnostic(
                "Module $path has no build.gradle(.kts).", "gradle.noBuildScript",
                DiagnosticSeverity.WARNING, relPath(projectRoot, dir),
            )
            return ModuleModel(path, name, ModuleType.UNKNOWN, dir)
        }

        val dsl = if (buildFile.name.endsWith(".kts")) GradleDsl.KOTLIN else GradleDsl.GROOVY
        val script = runCatching { BuildGradleParser.parse(buildFile.readText(), dsl) }
            .getOrElse {
                diagnostics += GradleDiagnostic(
                    "Couldn't parse ${buildFile.name}: ${it.message}", "gradle.parseFailure",
                    DiagnosticSeverity.ERROR, relPath(projectRoot, buildFile),
                )
                ParsedBuildScript(dsl)
            }

        val type = moduleType(script, catalog)
        val deps = resolveDependencies(script.dependencies, catalog, projectRoot, buildFile, diagnostics)
        val variants = variantsOf(path, script.android)
        val sourceSets = sourceSetsOf(dir, script.android)

        return ModuleModel(
            path = path,
            name = name,
            type = type,
            moduleDir = dir,
            variants = variants,
            sourceSets = sourceSets,
            dependencies = deps,
            applicationId = applicationIdOf(type, script.android),
        )
    }

    private fun applicationIdOf(type: ModuleType, android: ParsedAndroidBlock?): String? {
        if (type != ModuleType.ANDROID_APP || android == null) return null
        return (android.applicationId ?: android.namespace)?.takeIf { it.isNotBlank() }
    }

    private fun moduleType(script: ParsedBuildScript, catalog: VersionCatalog?): ModuleType {
        val ids = script.plugins.map { plugin ->


            if (plugin.fromCatalog) catalog?.findPlugin(plugin.id)?.id ?: plugin.id else plugin.id
        }
        return when {
            ids.any { it == "com.android.application" } -> ModuleType.ANDROID_APP
            ids.any { it == "com.android.library" || it == "com.android.dynamic-feature" } -> ModuleType.ANDROID_LIBRARY
            ids.any { it == "java-library" || it == "java" || it == "org.jetbrains.kotlin.jvm" } -> ModuleType.JVM
            else -> ModuleType.UNKNOWN
        }
    }

    private fun resolveDependencies(
        raw: List<RawDependency>,
        catalog: VersionCatalog?,
        projectRoot: File,
        buildFile: File,
        diagnostics: MutableList<GradleDiagnostic>,
    ): List<DependencyModel> {
        val out = ArrayList<DependencyModel>()
        for (dep in raw) {
            val scope = scopeOf(dep.configuration)
            when (dep.kind) {
                RawDependencyKind.MODULE ->
                    dep.coordinate?.let { out += DependencyModel(it, scope) }
                RawDependencyKind.PROJECT ->
                    dep.coordinate?.let { out += DependencyModel(it, scope) }
                RawDependencyKind.CATALOG -> {
                    val lib = catalog?.findLibrary(afterLibs(dep.catalogAccessor))
                    if (lib?.coordinate != null) out += DependencyModel(lib.coordinate!!, scope)
                    else diagnostics += unresolvedCatalog(dep.catalogAccessor, projectRoot, buildFile)
                }
                RawDependencyKind.CATALOG_BUNDLE -> {
                    val bundle = catalog?.findBundle(afterLibs(dep.catalogAccessor).removePrefix("bundles."))
                    if (bundle != null) {
                        bundle.forEach { alias ->
                            catalog.findLibrary(alias)?.coordinate?.let { out += DependencyModel(it, scope) }
                        }
                    } else diagnostics += unresolvedCatalog(dep.catalogAccessor, projectRoot, buildFile)
                }
                RawDependencyKind.UNKNOWN ->
                    diagnostics += GradleDiagnostic(
                        "Couldn't statically resolve a ${dep.configuration} dependency.",
                        "gradle.unresolvedDependency", DiagnosticSeverity.INFO, relPath(projectRoot, buildFile),
                    )
            }
        }
        return out
    }

    private fun unresolvedCatalog(accessor: String?, projectRoot: File, buildFile: File) = GradleDiagnostic(
        "Version-catalog reference '${accessor ?: "libs.?"}' isn't defined in libs.versions.toml.",
        "gradle.unresolvedCatalogRef", DiagnosticSeverity.WARNING, relPath(projectRoot, buildFile),
    )

    private fun afterLibs(accessor: String?): String = (accessor ?: "").removePrefix("libs.")

    private fun scopeOf(configuration: String): DependencyScope = when {
        configuration.startsWith("androidTest") -> DependencyScope.ANDROID_TEST
        configuration.startsWith("test") -> DependencyScope.TEST
        configuration == "api" || configuration.endsWith("Api") -> DependencyScope.API
        configuration == "compileOnly" || configuration.endsWith("CompileOnly") -> DependencyScope.COMPILE_ONLY
        configuration == "runtimeOnly" || configuration.endsWith("RuntimeOnly") -> DependencyScope.RUNTIME_ONLY
        configuration == "implementation" || configuration.endsWith("Implementation") -> DependencyScope.IMPLEMENTATION
        else -> DependencyScope.UNKNOWN
    }


    private fun variantsOf(modulePath: String, android: ParsedAndroidBlock?): List<VariantModel> {
        if (android == null) return emptyList()

        fun task(name: String): String = modulePath.trim().let { module ->
            if (module.isEmpty() || module == ":") name
            else "${if (module.startsWith(':')) module else ":$module"}:$name"
        }


        val buildTypes = effectiveBuildTypes(android.buildTypes)
        val flavors = android.productFlavors
        if (flavors.isEmpty()) {
            return buildTypes.map { bt ->
                val cap = bt.replaceFirstChar { it.uppercase() }
                VariantModel(name = bt, buildType = bt, assembleTaskPath = task("assemble$cap"), bundleTaskPath = task("bundle$cap"))
            }
        }
        return flavors.flatMap { flavor ->
            buildTypes.map { bt ->
                val name = flavor + bt.replaceFirstChar { c -> c.uppercase() }
                val cap = name.replaceFirstChar { it.uppercase() }
                VariantModel(name = name, buildType = bt, flavors = listOf(flavor), assembleTaskPath = task("assemble$cap"), bundleTaskPath = task("bundle$cap"))
            }
        }
    }

    internal fun effectiveBuildTypes(declared: List<String>): List<String> {
        val skip = setOf("getbyname", "maybecreate", "create", "named", "register", "matching", "configureeach")
        val extras = declared.map { it.trim() }.filter { it.isNotEmpty() && it.lowercase() !in skip }

        return LinkedHashSet<String>().apply {
            add("debug")
            add("release")
            addAll(extras)
        }.toList()
    }

    private fun sourceSetsOf(moduleDir: File, android: ParsedAndroidBlock?): List<SourceSetModel> {
        val names = LinkedHashSet<String>().apply {
            add("main")
            add("test")
            add("androidTest")
            addAll(android?.buildTypes.orEmpty())
            addAll(android?.productFlavors.orEmpty())
        }
        val srcRoot = File(moduleDir, "src")
        return names.mapNotNull { name ->
            val base = File(srcRoot, name)

            if (name != "main" && !base.exists()) return@mapNotNull null
            val manifest = File(base, "AndroidManifest.xml").takeIf { it.isFile }
            SourceSetModel(
                name = name,
                javaDirs = listOf(File(base, "java")).filter { it.exists() || name == "main" },
                kotlinDirs = listOf(File(base, "kotlin")).filter { it.exists() },
                resDirs = listOf(File(base, "res")).filter { it.exists() || name == "main" },
                assetsDirs = listOf(File(base, "assets")).filter { it.exists() },
                manifestFile = manifest,
            )
        }
    }


    private fun readCatalog(projectRoot: File, diagnostics: MutableList<GradleDiagnostic>): VersionCatalog? {
        val toml = File(projectRoot, "gradle/libs.versions.toml")
        if (!toml.isFile) return null
        return runCatching { VersionCatalogParser.parse(toml.readText()) }.getOrElse {
            diagnostics += GradleDiagnostic(
                "Couldn't parse libs.versions.toml: ${it.message}", "gradle.catalogParseFailure",
                DiagnosticSeverity.ERROR, "gradle/libs.versions.toml",
            )
            null
        }
    }

    private fun readGradleVersion(projectRoot: File, diagnostics: MutableList<GradleDiagnostic>): String? {
        val wrapper = File(projectRoot, "gradle/wrapper/gradle-wrapper.properties")
        if (!wrapper.isFile) return null
        val version = GradlePropertiesParser.gradleVersionFromWrapper(readProperties(wrapper))
        if (version == null) {
            diagnostics += GradleDiagnostic(
                "Couldn't determine the Gradle version from the wrapper.", "gradle.unknownWrapperVersion",
                DiagnosticSeverity.INFO, "gradle/wrapper/gradle-wrapper.properties",
            )
        }
        return version
    }

    private fun readProperties(file: File): Map<String, String> =
        if (file.isFile) runCatching { GradlePropertiesParser.parse(file.readText()) }.getOrDefault(emptyMap())
        else emptyMap()

    private fun resolveModuleDir(projectRoot: File, path: String, overrides: Map<String, String>): File {
        overrides[path]?.let { return resolveRelative(projectRoot, it) }

        val rel = path.trimStart(':').replace(':', '/')
        return File(projectRoot, rel)
    }

    private fun resolveRelative(projectRoot: File, pathText: String): File {
        val f = File(pathText)
        return if (f.isAbsolute) f else File(projectRoot, pathText)
    }

    private fun firstExisting(dir: File, vararg names: String): File? =
        names.map { File(dir, it) }.firstOrNull { it.isFile }

    private fun relPath(root: File, file: File): String =
        runCatching { file.relativeTo(root).path }.getOrDefault(file.path)
}
