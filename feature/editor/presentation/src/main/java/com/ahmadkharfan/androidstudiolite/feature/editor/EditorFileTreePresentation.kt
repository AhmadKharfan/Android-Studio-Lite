package com.ahmadkharfan.androidstudiolite.feature.editor

import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileState
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileStatus
import java.io.File

internal fun FileNode.toEditorNode(
    projectRoot: String?,
    gitFiles: Map<String, GitFileState>,
): EditorFileNodeUiModel = EditorFileNodeUiModel(
    id = id,
    name = name,
    children = children?.map { it.toEditorNode(projectRoot, gitFiles) },
    icon = if (children != null) null else fileIconFor(name),
    gitStatus = layeredGitStatus(id, projectRoot?.let(::File), gitFiles) ?: gitStatus,
)

internal fun EditorFileNodeUiModel.withGitStatus(
    root: File,
    gitFiles: Map<String, GitFileState>,
): EditorFileNodeUiModel = copy(
    children = children?.map { it.withGitStatus(root, gitFiles) },
    gitStatus = if (children != null) null else layeredGitStatus(id, root, gitFiles),
)

internal fun layeredGitStatus(
    path: String,
    projectRoot: File?,
    gitFiles: Map<String, GitFileState>,
): GitFileStatus? {
    projectRoot ?: return null
    val file = runCatching { File(path).canonicalFile }.getOrDefault(File(path).absoluteFile)
    val canonicalRoot = runCatching { projectRoot.canonicalFile }.getOrDefault(projectRoot.absoluteFile)
    val relative = runCatching { file.relativeTo(canonicalRoot).invariantSeparatorsPath }.getOrNull()
        ?: return null
    return gitFiles[relative]?.toEditorFileStatus()
}
