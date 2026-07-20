package com.ahmadkharfan.androidstudiolite.feature.editor.engine.project

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleModel
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ProjectModel
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.CompletionKind
import java.io.File
import java.util.zip.ZipFile

object ProjectSymbolIndexer {

    private const val MAX_SYMBOLS = 20_000

    private const val MAX_SOURCE_BYTES = 512L * 1024

    private val SOURCE_EXTENSIONS = setOf("kt", "java")

    fun index(model: ProjectModel, openModulePath: String? = null): ProjectSymbolIndex {
        val openModule = openModulePath?.let { path -> model.modules.firstOrNull { it.path == path } }
            ?: model.modules.firstOrNull()
            ?: return ProjectSymbolIndex.EMPTY

        val symbols = ArrayList<ProjectSymbol>()
        val seen = HashSet<String>()

        indexModuleSources(openModule, symbols, seen)
        indexDependencies(model, symbols, seen)

        return ProjectSymbolIndex(symbols)
    }


    private fun indexModuleSources(module: ModuleModel, out: MutableList<ProjectSymbol>, seen: MutableSet<String>) {
        val roots = module.sourceSets.flatMap { it.javaDirs + it.kotlinDirs }.filter { it.isDirectory }
        for (root in roots) {
            root.walkTopDown()
                .filter { it.isFile && it.extension.lowercase() in SOURCE_EXTENSIONS && it.length() <= MAX_SOURCE_BYTES }
                .forEach { file ->
                    if (out.size >= MAX_SYMBOLS) return
                    runCatching { indexSourceFile(file.readText(), out, seen) }
                }
        }
    }

    internal fun indexSourceFile(text: String, out: MutableList<ProjectSymbol>, seen: MutableSet<String>) {
        val pkg = PACKAGE_REGEX.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        for (match in DECLARATION_REGEX.findAll(text)) {
            if (out.size >= MAX_SYMBOLS) return
            val keyword = match.groupValues[1]
            val name = match.groupValues[2]
            if (name.isEmpty() || !name[0].isLetter() && name[0] != '_') continue
            val kind = when (keyword) {
                "fun" -> CompletionKind.Function
                "val", "var", "const" -> CompletionKind.Property
                else -> CompletionKind.Class
            }
            val qualified = if (pkg.isEmpty()) name else "$pkg.$name"
            if (seen.add("P:$qualified")) {
                out += ProjectSymbol(name, qualified, kind, pkg.ifEmpty { null }, SymbolOrigin.PROJECT)
            }
        }
    }


    private fun indexDependencies(model: ProjectModel, out: MutableList<ProjectSymbol>, seen: MutableSet<String>) {
        val artifacts = model.modules.asSequence()
            .flatMap { it.dependencies.asSequence() }
            .mapNotNull { it.resolvedArtifact }
            .filter { it.isFile }
            .distinctBy { it.absolutePath }
        for (artifact in artifacts) {
            if (out.size >= MAX_SYMBOLS) return
            runCatching { indexArtifact(artifact, out, seen) }
        }
    }

    private fun indexArtifact(artifact: File, out: MutableList<ProjectSymbol>, seen: MutableSet<String>) {
        when (artifact.extension.lowercase()) {
            "jar" -> indexClassJar(artifact, out, seen)
            "aar" -> indexAar(artifact, out, seen)
        }
    }

    private fun indexAar(aar: File, out: MutableList<ProjectSymbol>, seen: MutableSet<String>) {
        ZipFile(aar).use { zip ->
            val classesEntry = zip.getEntry("classes.jar") ?: return
            val tmp = File.createTempFile("asl-aar-classes", ".jar")
            try {
                zip.getInputStream(classesEntry).use { input -> tmp.outputStream().use { input.copyTo(it) } }
                indexClassJar(tmp, out, seen)
            } finally {
                tmp.delete()
            }
        }
    }

    private fun indexClassJar(jar: File, out: MutableList<ProjectSymbol>, seen: MutableSet<String>) {
        ZipFile(jar).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                if (out.size >= MAX_SYMBOLS) return
                val entry = entries.nextElement()
                if (entry.isDirectory) continue
                val name = entry.name
                if (!name.endsWith(".class")) continue
                val fqcn = classNameFromEntry(name) ?: continue
                val simple = fqcn.substringAfterLast('.')
                val pkg = fqcn.substringBeforeLast('.', "")
                if (seen.add("D:$fqcn")) {
                    out += ProjectSymbol(simple, fqcn, CompletionKind.Class, pkg.ifEmpty { null }, SymbolOrigin.DEPENDENCY)
                }
            }
        }
    }

    internal fun classNameFromEntry(entryName: String): String? {
        if ('$' in entryName) return null
        val path = entryName.removeSuffix(".class")
        val simple = path.substringAfterLast('/')
        if (simple == "package-info" || simple == "module-info") return null
        if (simple.isEmpty() || !(simple[0].isLetter() || simple[0] == '_')) return null
        return path.replace('/', '.')
    }

    private val PACKAGE_REGEX = Regex("""(?m)^\s*package\s+([\w.]+)""")

    private val DECLARATION_REGEX =
        Regex("""\b(class|interface|object|enum|annotation|fun|val|var|const|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)""")
}
