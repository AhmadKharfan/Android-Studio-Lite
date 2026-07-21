package com.ahmadkharfan.androidstudiolite.feature.editor
import androidx.compose.runtime.Immutable
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslLineGit
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileState
import com.ahmadkharfan.androidstudiolite.domain.model.GitHeadState
import com.ahmadkharfan.androidstudiolite.domain.model.GitIndexStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitRepositoryState
import com.ahmadkharfan.androidstudiolite.domain.model.GitState
import com.ahmadkharfan.androidstudiolite.domain.model.GitWorktreeStatus
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildConsoleState
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.EditorLanguage
@Immutable
data class EditorTabUiModel(
    val id: String,
    val name: String,
    val text: String,
    val language: EditorLanguage,
    val modified: Boolean,
    val breadcrumb: List<String>,
    val breakpoints: Set<Int> = emptySet(),
    val gitLineStatus: Map<Int, AslLineGit> = emptyMap(),
)
@Immutable
data class EditorFileNodeUiModel(
    val id: String,
    val name: String,
    val children: List<EditorFileNodeUiModel>? = null,
    val icon: String? = null,
    val gitStatus: GitFileStatus? = null,
)
enum class EditorRailTool { Files, Git, AiAgent, Variants, Assets }
@Immutable
data class BottomPanelTabUiModel(
    val id: String,
    val label: String,
    val icon: String,
    val count: Int? = null,
    val error: Boolean = false,
)
@Immutable
data class BuildOutputLineUiModel(
    val text: String,
    val depth: Int = 0,
    val status: String? = null,
    val duration: String? = null,
    val jumpToTabId: String? = null,
)
@Immutable
data class InstallConflictUiModel(
    val applicationId: String,
)

enum class EditorFileCreateKind { File, Folder }

enum class EditorFileTreeAction {
    NewFile,
    NewFolder,
    Rename,
    Copy,
    Paste,
    Delete,
    ShowHistory,
    Blame,
    AddToGitignore,
}

@Immutable
data class CopiedFileTreeEntryUiModel(
    val path: String,
    val name: String,
)

@Immutable
sealed interface EditorFileOperationDialogUiState {
    data object None : EditorFileOperationDialogUiState
    data class Create(
        val parentPath: String,
        val parentName: String,
        val kind: EditorFileCreateKind,
    ) : EditorFileOperationDialogUiState
    data class Rename(
        val path: String,
        val currentName: String,
    ) : EditorFileOperationDialogUiState
    data class Delete(
        val path: String,
        val name: String,
        val isDirectory: Boolean,
    ) : EditorFileOperationDialogUiState
}

@Immutable
data class EditorUiState(
    val projectId: String = "",
    val projectName: String = "",
    val projectRootPath: String = "",
    val tabs: List<EditorTabUiModel> = emptyList(),
    val activeTabId: String? = null,
    val running: Boolean = false,
    val openRailTool: EditorRailTool? = null,
    val gitStatusText: String? = null,
    val gitPendingChangeCount: Int = 0,
    val fileTree: List<EditorFileNodeUiModel> = emptyList(),
    val expandedFolderIds: Set<String> = emptySet(),
    val selectedFileTreeId: String? = null,
    val bottomPanelHeightDp: Float = 0f,
    val bottomPanelTabs: List<BottomPanelTabUiModel> = emptyList(),
    val activeBottomTabId: String = "build",
    val buildProgressPercent: Int? = null,
    val buildLines: List<BuildOutputLineUiModel> = emptyList(),
    val buildFailed: Boolean = false,
    val buildConsole: BuildConsoleState = BuildConsoleState(),
    val snackbarMessage: String? = null,
    val installConflict: InstallConflictUiModel? = null,
    val fileOperationDialog: EditorFileOperationDialogUiState = EditorFileOperationDialogUiState.None,
    val copiedFileTreeEntry: CopiedFileTreeEntryUiModel? = null,
    val findBarOpen: Boolean = false,
    val findQuery: String = "",
    val findMatchCount: Int = 0,
    val findCurrentMatch: Int = 0,
    val markdownPreview: Boolean = true,
    val isLoadingFileTree: Boolean = true,
    val autocompletePopupVisible: Boolean = false,
    val editorFontSize: Int = 13,
    val editorTabSize: Int = 4,
    val editorThemeId: String = "darcula",
    val editorFontFamily: String = "jetbrains",
    val selectedVariant: String = "debug",
    val availableVariants: List<String> = listOf("debug", "release"),
    val runModulePath: String = ":app",
    val buildOutputAab: Boolean = false,
    val caretLine: Int = 0,
    val caretColumn: Int = 0,
    val editorRevealNonce: Int = 0,
    val editorRevealOffset: Int = 0,
    val projectIndex: com.ahmadkharfan.androidstudiolite.feature.editor.engine.project.ProjectSymbolIndex =
        com.ahmadkharfan.androidstudiolite.feature.editor.engine.project.ProjectSymbolIndex.EMPTY,
) {
    val activeTab: EditorTabUiModel?
        get() = tabs.firstOrNull { it.id == activeTabId }
    val gitBadge: String? get() = gitPendingChangeCount.takeIf { it > 0 }?.toString()
    val releaseBuildLabel: String get() = if (buildOutputAab) "Build AAB (release)" else "Build APK (release)"
}

internal data class EditorGitUiModel(val statusText: String?, val pendingChangeCount: Int)

internal fun GitFileState.toEditorFileStatus(): GitFileStatus? = when {
    conflictStage != null -> GitFileStatus.CONFLICTED
    worktreeStatus == GitWorktreeStatus.DELETED || indexStatus == GitIndexStatus.DELETED -> GitFileStatus.DELETED
    worktreeStatus == GitWorktreeStatus.UNTRACKED -> GitFileStatus.UNTRACKED
    indexStatus == GitIndexStatus.ADDED -> GitFileStatus.ADDED
    worktreeStatus == GitWorktreeStatus.MODIFIED ||
        indexStatus in setOf(GitIndexStatus.MODIFIED, GitIndexStatus.RENAMED) -> GitFileStatus.MODIFIED
    else -> null
}

internal fun GitState.toEditorGitUiModel(): EditorGitUiModel {
    if (!isRepository) return EditorGitUiModel(statusText = null, pendingChangeCount = 0)
    val head = when (val value = headState) {
        is GitHeadState.Branch -> value.name
        is GitHeadState.Detached -> "HEAD (${value.shortSha})"
        GitHeadState.Unborn -> "HEAD"
    }
    val pending = files.count { it.hasPendingChange }
    val operation = when (repositoryState) {
        GitRepositoryState.SAFE -> null
        GitRepositoryState.MERGING -> "Merging"
        GitRepositoryState.REBASING -> "Rebasing"
        GitRepositoryState.CHERRY_PICKING -> "Cherry-picking"
        GitRepositoryState.REVERTING -> "Reverting"
        GitRepositoryState.BISECTING -> "Bisecting"
    }
    return EditorGitUiModel(
        statusText = buildString {
            append(head)
            if (pending > 0) append(" ●")
            operation?.let { append(", ").append(it) }
        },
        pendingChangeCount = pending,
    )
}
