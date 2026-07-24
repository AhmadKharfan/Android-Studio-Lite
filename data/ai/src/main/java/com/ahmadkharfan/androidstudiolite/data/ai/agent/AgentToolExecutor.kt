package com.ahmadkharfan.androidstudiolite.data.ai.agent

import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.ModuleType
import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction
import com.ahmadkharfan.androidstudiolite.domain.model.AgentToolResult
import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import com.ahmadkharfan.androidstudiolite.domain.repository.FileContentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.FileTreeRepository
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AgentToolExecutor(
    private val fileContentRepository: FileContentRepository,
    private val fileTreeRepository: FileTreeRepository,
    private val projectPathResolver: ProjectPathResolver,
    private val gradleProjectReader: GradleProjectReader,
) : AgentTools {

    override suspend fun sourcePackagePrefix(projectId: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = projectRoot(projectId)
                val appModule = gradleProjectReader.read(root).model.modules.firstOrNull { module ->
                    module.type == ModuleType.ANDROID_APP || module.path == ":app"
                } ?: return@runCatching null
                val pkg = appModule.applicationId?.takeIf { it.isNotBlank() } ?: return@runCatching null
                val sourceRoot = appModule.sourceSets.firstOrNull { it.name == "main" }?.let { sourceSet ->
                    sourceSet.javaDirs.firstOrNull() ?: sourceSet.kotlinDirs.firstOrNull()
                }
                    ?: appModule.sourceSets.firstOrNull()?.javaDirs?.firstOrNull()
                    ?: appModule.sourceSets.firstOrNull()?.kotlinDirs?.firstOrNull()
                    ?: File(appModule.moduleDir, "src/main/java")
                val relativeRoot = relativePath(root, sourceRoot).trimEnd('/')
                "$relativeRoot/${pkg.replace('.', '/')}/"
            }.getOrNull()
        }

    override suspend fun projectLanguage(projectId: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val root = projectRoot(projectId)
            var java = false
            var kotlin = false
            root.walkTopDown()
                .onEnter { !isIgnoredDir(it) }
                .filter(File::isFile)
                .forEach { file ->
                    java = java || file.extension.equals("java", ignoreCase = true)
                    kotlin = kotlin || file.extension.equals("kt", ignoreCase = true)
                }
            when {
                java && kotlin -> "Java + Kotlin"
                java -> "Java"
                kotlin -> "Kotlin"
                else -> "Unknown"
            }
        }.getOrDefault("Unknown")
    }

    override suspend fun projectRoot(projectId: String): File =
        withContext(Dispatchers.IO) { projectPathResolver(projectId).canonicalFile }

    override suspend fun readTextOrNull(projectId: String, relativePath: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = projectRoot(projectId)
                val file = resolve(root, relativePath)
                if (file.isFile) fileContentRepository.readText(file.absolutePath) else null
            }.getOrNull()
        }

    override suspend fun outline(projectId: String, maxEntries: Int): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = projectRoot(projectId)
                val lines = ArrayList<String>()
                fun walk(nodes: List<FileNode>, depth: Int) {
                    if (lines.size >= maxEntries) return
                    for (node in nodes) {
                        if (lines.size >= maxEntries) return
                        val relative = relativePath(root, File(node.id))
                        val isDir = node.children != null
                        lines.add("  ".repeat(depth) + relative.substringAfterLast('/') + if (isDir) "/" else "")
                        if (isDir && depth < 3) walk(node.children.orEmpty(), depth + 1)
                    }
                }
                walk(fileTreeRepository.getFileTree(projectId), 0)
                if (lines.isEmpty()) "(empty project)" else lines.joinToString("\n")
            }.getOrElse { "(unable to read project tree: ${it.message})" }
        }

    override suspend fun run(projectId: String, action: AgentAction): AgentToolResult =
        withContext(Dispatchers.IO) {
            val root = projectRoot(projectId)
            val normalized = normalizeAction(root, action)
            runCatching { execute(projectId, root, normalized) }
                .fold(
                    onSuccess = { AgentToolResult(normalized, ok = true, output = it) },
                    onFailure = { AgentToolResult(normalized, ok = false, output = it.message ?: "Error") },
                )
        }

    override fun normalizeAction(root: File, action: AgentAction): AgentAction = when (action) {
        is AgentAction.CreateFile -> {
            val path = normalizeSourcePath(root, action.path, action.content)
            val content = AgentContentSanitizer.sanitizeFileContent(path, action.content)
            action.copy(path = path, content = content)
        }
        is AgentAction.EditFile -> {
            val path = normalizeSourcePath(root, action.path, action.content)
            val content = AgentContentSanitizer.sanitizeFileContent(path, action.content)
            action.copy(path = path, content = content)
        }
        else -> action
    }

    private suspend fun execute(projectId: String, root: File, action: AgentAction): String = when (action) {
        is AgentAction.ListDir -> {
            val dir = resolve(root, action.path)
            require(dir.isDirectory) { "Not a directory: ${action.path}" }
            val children = fileTreeRepository.listChildren(dir.absolutePath)
            if (children.isEmpty()) {
                "(empty)"
            } else {
                children.joinToString("\n") { child ->
                    val isDir = child.children != null || File(child.id).isDirectory
                    relativePath(root, File(child.id)) + if (isDir) "/" else ""
                }
            }
        }

        is AgentAction.ReadFile -> {
            val file = resolve(root, action.path)
            require(file.isFile) { "Not a file: ${action.path}" }
            val text = fileContentRepository.readText(file.absolutePath)
            "```\n$text\n```"
        }

        is AgentAction.Search -> search(root, action.query)

        is AgentAction.CreateFile -> {
            val path = action.path
            val file = resolve(root, path)
            require(!file.exists()) { "Already exists: $path (use edit_file to overwrite)" }
            fileContentRepository.writeText(file.absolutePath, action.content)
            "Created $path"
        }

        is AgentAction.CreateDir -> {
            val dir = resolve(root, action.path)
            if (dir.isDirectory) {
                "Already exists: ${action.path}"
            } else {
                val parent = dir.parentFile ?: root
                fileTreeRepository.createDirectory(parent.absolutePath, dir.name)
                "Created directory ${action.path}"
            }
        }

        is AgentAction.EditFile -> {
            val path = action.path
            val file = resolve(root, path)
            require(file.isFile) { "No such file: $path (use create_file for new files)" }
            fileContentRepository.writeText(file.absolutePath, action.content)
            "Updated $path"
        }

        is AgentAction.Rename -> {
            val file = resolve(root, action.path)
            require(file.exists()) { "No such entry: ${action.path}" }
            require(!action.newName.contains('/') && !action.newName.contains('\\')) {
                "newName must be a single segment: ${action.newName}"
            }
            val newPath = fileTreeRepository.rename(file.absolutePath, action.newName)
            "Renamed to ${relativePath(root, File(newPath))}"
        }

        is AgentAction.Move -> {
            val file = resolve(root, action.path)
            require(file.exists()) { "No such entry: ${action.path}" }
            val newParent = resolve(root, action.newParent)
            require(newParent.isDirectory) { "Not a directory: ${action.newParent}" }
            val newPath = fileTreeRepository.move(file.absolutePath, newParent.absolutePath)
            "Moved to ${relativePath(root, File(newPath))}"
        }

        is AgentAction.Delete -> {
            val file = resolve(root, action.path)
            require(file.exists()) { "No such entry: ${action.path}" }
            fileTreeRepository.delete(file.absolutePath)
            "Deleted ${action.path}"
        }
    }

    private fun search(root: File, query: String): String {
        require(query.isNotBlank()) { "Empty query" }
        val needle = query.lowercase()
        val matches = ArrayList<String>()
        root.walkTopDown()
            .onEnter { dir -> !isIgnoredDir(dir) }
            .filter { it.isFile && it.length() <= MAX_SEARCH_BYTES }
            .forEach { file ->
                if (matches.size >= MAX_MATCHES) return@forEach
                val relative = relativePath(root, file)
                if (relative.lowercase().contains(needle)) matches.add("$relative (filename)")
                runCatching { file.readText() }.getOrNull()?.let { content ->
                    content.lineSequence().forEachIndexed { index, line ->
                        if (matches.size < MAX_MATCHES && line.lowercase().contains(needle)) {
                            matches.add("$relative:${index + 1}: ${line.trim().take(160)}")
                        }
                    }
                }
            }
        return if (matches.isEmpty()) "No matches for \"$query\"" else matches.take(MAX_MATCHES).joinToString("\n")
    }

    private fun resolve(root: File, path: String): File {
        val cleaned = path.trim().removePrefix("./").trimStart('/')
        val file = if (cleaned.isEmpty() || cleaned == ".") root else File(root, cleaned)
        val canonical = file.canonicalFile
        require(isWithin(root, canonical)) { "Path escapes the project: $path" }
        require(canonical.relativeToOrNull(root)?.path?.split('/', '\\')?.none { it == ".git" } != false) {
            "The .git directory is off-limits: $path"
        }
        return canonical
    }

    private fun isWithin(root: File, candidate: File): Boolean {
        var cur: File? = candidate
        val target = root.canonicalFile
        while (cur != null) {
            if (cur == target) return true
            cur = cur.parentFile
        }
        return false
    }

    private fun relativePath(root: File, file: File): String =
        file.canonicalFile.relativeToOrNull(root.canonicalFile)?.path?.replace('\\', '/')
            ?: file.absolutePath

    private fun normalizeSourcePath(root: File, path: String, content: String): String {
        val cleaned = path.trim().removePrefix("./").trimStart('/')
        val fileName = File(cleaned).name
        if (!fileName.endsWith(".kt", ignoreCase = true) && !fileName.endsWith(".java", ignoreCase = true)) {
            return cleaned
        }
        val pkg = PACKAGE_REGEX.find(content)?.groupValues?.get(1) ?: return cleaned
        val sourceRoot = detectSourceRoot(root, cleaned)
        val expected = "$sourceRoot/${pkg.replace('.', '/')}/$fileName"
        if (cleaned == expected) return cleaned
        val segments = cleaned.removeSuffix(fileName).trimEnd('/').substringAfterLast('/', "")
        if (segments.isEmpty() || !segments.contains('.')) return expected
        return cleaned
    }

    private fun detectSourceRoot(root: File, path: String): String {
        for (marker in listOf("src/main/java", "src/main/kotlin")) {
            val idx = path.indexOf(marker)
            if (idx >= 0) return path.substring(0, idx + marker.length)
        }
        val appModule = File(root, "app")
        if (appModule.isDirectory) return "app/src/main/java"
        return "app/src/main/java"
    }

    private companion object {
        val PACKAGE_REGEX = Regex("""^package\s+([\w.]+)""", RegexOption.MULTILINE)
        val IGNORED_DIRS = setOf(".git", ".gradle", ".idea", "build", ".cxx", "caches", ".kotlin")
        const val MAX_SEARCH_BYTES = 512L * 1024
        const val MAX_MATCHES = 60

        fun isIgnoredDir(dir: File): Boolean {
            if (dir.name !in IGNORED_DIRS) return false
            if (dir.name != "build") return true
            val parent = dir.parentFile ?: return false
            val parentIsGradleProject =
                File(parent, "build.gradle.kts").isFile || File(parent, "build.gradle").isFile ||
                    File(parent, "settings.gradle.kts").isFile || File(parent, "settings.gradle").isFile
            val selfIsModule =
                File(dir, "build.gradle.kts").isFile || File(dir, "build.gradle").isFile ||
                    File(dir, "settings.gradle.kts").isFile || File(dir, "settings.gradle").isFile ||
                    File(dir, "src").isDirectory
            return parentIsGradleProject && !selfIsModule
        }
    }
}
