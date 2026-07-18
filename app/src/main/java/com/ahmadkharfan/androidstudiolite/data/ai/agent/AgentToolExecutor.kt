package com.ahmadkharfan.androidstudiolite.data.ai.agent

import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction
import com.ahmadkharfan.androidstudiolite.domain.model.AgentToolResult
import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import com.ahmadkharfan.androidstudiolite.domain.repository.FileContentRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.FileTreeRepository
import com.ahmadkharfan.androidstudiolite.domain.usecase.ProjectPathResolver
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs [AgentAction]s against the open project's working tree. Every project-relative path is resolved
 * against the project root and validated so the agent can never read or write outside the project (or
 * into the `.git` directory). File writes route through [FileContentRepository]/[FileTreeRepository] so
 * the shared FileChangeBus fires and open editor tabs stay in sync.
 */
class AgentToolExecutor(
    private val fileContentRepository: FileContentRepository,
    private val fileTreeRepository: FileTreeRepository,
    private val projectPathResolver: ProjectPathResolver,
) {

    suspend fun projectRoot(projectId: String): File =
        withContext(Dispatchers.IO) { projectPathResolver(projectId).canonicalFile }

    /** Reads a file's current text, or null if it does not exist — used to build edit diffs. */
    suspend fun readTextOrNull(projectId: String, relativePath: String): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = projectRoot(projectId)
                val file = resolve(root, relativePath)
                if (file.isFile) fileContentRepository.readText(file.absolutePath) else null
            }.getOrNull()
        }

    /** A shallow, project-relative listing used to seed the agent's system prompt. */
    suspend fun outline(projectId: String, maxEntries: Int = 250): String =
        withContext(Dispatchers.IO) {
            runCatching {
                val root = projectRoot(projectId)
                val lines = ArrayList<String>()
                fun walk(nodes: List<FileNode>, depth: Int) {
                    if (lines.size >= maxEntries) return
                    for (node in nodes) {
                        if (lines.size >= maxEntries) return
                        val rel = rel(root, File(node.id))
                        val isDir = node.children != null
                        lines.add("  ".repeat(depth) + rel.substringAfterLast('/') + if (isDir) "/" else "")
                        if (isDir && depth < 3) walk(node.children.orEmpty(), depth + 1)
                    }
                }
                walk(fileTreeRepository.getFileTree(projectId), 0)
                if (lines.isEmpty()) "(empty project)" else lines.joinToString("\n")
            }.getOrElse { "(unable to read project tree: ${it.message})" }
        }

    suspend fun run(projectId: String, action: AgentAction): AgentToolResult =
        withContext(Dispatchers.IO) {
            val root = projectRoot(projectId)
            runCatching { execute(projectId, root, action) }
                .fold(
                    onSuccess = { AgentToolResult(action, ok = true, output = it) },
                    onFailure = { AgentToolResult(action, ok = false, output = it.message ?: "Error") },
                )
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
                    rel(root, File(child.id)) + if (isDir) "/" else ""
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
            val file = resolve(root, action.path)
            require(!file.exists()) { "Already exists: ${action.path} (use edit_file to overwrite)" }
            fileContentRepository.writeText(file.absolutePath, action.content)
            "Created ${action.path}"
        }

        is AgentAction.CreateDir -> {
            val dir = resolve(root, action.path)
            if (dir.isDirectory) {
                "Already exists: ${action.path}"
            } else {
                require(dir.mkdirs()) { "Could not create directory: ${action.path}" }
                "Created directory ${action.path}"
            }
        }

        is AgentAction.EditFile -> {
            val file = resolve(root, action.path)
            require(file.isFile) { "No such file: ${action.path} (use create_file for new files)" }
            fileContentRepository.writeText(file.absolutePath, action.content)
            "Updated ${action.path}"
        }

        is AgentAction.Rename -> {
            val file = resolve(root, action.path)
            require(file.exists()) { "No such entry: ${action.path}" }
            require(!action.newName.contains('/') && !action.newName.contains('\\')) {
                "newName must be a single segment: ${action.newName}"
            }
            val newPath = fileTreeRepository.rename(file.absolutePath, action.newName)
            "Renamed to ${rel(root, File(newPath))}"
        }

        is AgentAction.Move -> {
            val file = resolve(root, action.path)
            require(file.exists()) { "No such entry: ${action.path}" }
            val newParent = resolve(root, action.newParent)
            require(newParent.isDirectory) { "Not a directory: ${action.newParent}" }
            val newPath = fileTreeRepository.move(file.absolutePath, newParent.absolutePath)
            "Moved to ${rel(root, File(newPath))}"
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
            .onEnter { dir -> dir.name !in IGNORED_DIRS }
            .filter { it.isFile && it.length() <= MAX_SEARCH_BYTES }
            .forEach { file ->
                if (matches.size >= MAX_MATCHES) return@forEach
                val rel = rel(root, file)
                if (rel.lowercase().contains(needle)) matches.add("$rel (filename)")
                runCatching { file.readText() }.getOrNull()?.let { content ->
                    content.lineSequence().forEachIndexed { index, line ->
                        if (matches.size < MAX_MATCHES && line.lowercase().contains(needle)) {
                            matches.add("$rel:${index + 1}: ${line.trim().take(160)}")
                        }
                    }
                }
            }
        return if (matches.isEmpty()) "No matches for \"$query\"" else matches.take(MAX_MATCHES).joinToString("\n")
    }

    /** Resolves a project-relative path to a canonical file inside [root], rejecting traversal + `.git`. */
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

    private fun rel(root: File, file: File): String =
        file.canonicalFile.relativeToOrNull(root.canonicalFile)?.path?.replace('\\', '/')
            ?: file.absolutePath

    private companion object {
        val IGNORED_DIRS = setOf(".git", ".gradle", ".idea", "build", ".cxx", "caches", ".kotlin")
        const val MAX_SEARCH_BYTES = 512L * 1024
        const val MAX_MATCHES = 60
    }
}
