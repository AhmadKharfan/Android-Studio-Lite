package com.ahmadkharfan.androidstudiolite.feature.editor.git
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.ahmadkharfan.androidstudiolite.designsystem.animation.AslSlideContent
import com.ahmadkharfan.androidstudiolite.designsystem.animation.AslStateCrossfade
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslOverflowMenu
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslOverflowMenuEntry
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslDiffLine
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslEmptyState
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslCheckbox
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChip
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChipKind
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChipStatus
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSwitch
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialog
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialogVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslToolWindowPanel
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.rememberAslToolWindowWidth
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslShape
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget
import com.ahmadkharfan.androidstudiolite.domain.model.PullMode
import com.ahmadkharfan.androidstudiolite.domain.model.GitRepositoryState
import com.ahmadkharfan.androidstudiolite.designsystem.icon.AslIcon
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitChangeUiModel
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitPanelUiState
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitPanelViewModel
import com.ahmadkharfan.androidstudiolite.feature.git.middleEllipsis

@Composable
fun GitPanelRoute(
    projectId: String,
    onClose: () -> Unit,
    onOpenDiff: (String, GitDiffTarget) -> Unit = { _, _ -> },
    onOpenHistory: () -> Unit = {},
    onOpenBranches: () -> Unit = {},
    onOpenTags: () -> Unit = {},
    onOpenStashes: () -> Unit = {},
    onOpenConflicts: () -> Unit = {},
    viewModel: GitPanelViewModel = koinViewModel { parametersOf(projectId) },
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.onAppForegrounded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    GitPanelScreen(
        uiState = uiState,
        interactionListener = viewModel,
        onClose = onClose,
        onOpenDiff = onOpenDiff,
        onOpenHistory = onOpenHistory,
        onOpenBranches = onOpenBranches,
        onOpenTags = onOpenTags,
        onOpenStashes = onOpenStashes,
        onOpenConflicts = onOpenConflicts,
    )
}

@Composable
private fun GitPanelScreen(
    uiState: GitPanelUiState,
    interactionListener: GitPanelInteractionListener,
    onClose: () -> Unit,
    onOpenDiff: (String, GitDiffTarget) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenBranches: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenStashes: () -> Unit,
    onOpenConflicts: () -> Unit,
) {


    val inSubView = uiState.remotesVisible || uiState.submodulesVisible
    AslToolWindowPanel(
        title = when {
            uiState.remotesVisible -> "Git remotes"
            uiState.submodulesVisible -> "Git submodules"
            else -> "Git · ${uiState.branch}"
        },
        width = rememberAslToolWindowWidth(),
        onClose = when {
            uiState.remotesVisible -> interactionListener::onCloseRemotes
            uiState.submodulesVisible -> interactionListener::onCloseSubmodules
            else -> onClose
        },
        closeIcon = if (inSubView) "arrow-left" else "x",
        closeContentDescription = if (inSubView) "Back" else "Close panel",
        scrollable = false,
        actions = if (!inSubView && uiState.isRepository) {
            {
                AslIconButton(
                    icon = "git-branch",
                    contentDescription = "Branches",
                    onClick = onOpenBranches,
                    size = 32.dp,
                    iconSize = 16.dp,
                    disabled = uiState.isBusy,
                )
                GitActionsOverflowMenu(
                    uiState = uiState,
                    interactionListener = interactionListener,
                    onOpenHistory = onOpenHistory,
                    onOpenStashes = onOpenStashes,
                )
            }
        } else {
            null
        },
    ) {
        if (!uiState.isRepository) {
            AslEmptyState(
                icon = "git-branch",
                title = "Not a git repository",
                subtitle = "Clone a repository or initialise git for this project to see changes here.",
                actionLabel = "Enable version control",
                onAction = interactionListener::onOpenBootstrap,
                modifier = Modifier.fillMaxSize(),
            )
            return@AslToolWindowPanel
        }
        if (uiState.remotesVisible) {
            RemotesView(uiState, interactionListener)
            return@AslToolWindowPanel
        }
        if (uiState.submodulesVisible) {
            SubmodulesView(uiState, interactionListener)
            return@AslToolWindowPanel
        }


        AslSlideContent(
            targetState = uiState,
            contentKey = { it.selectedPath != null },
            isForward = { initial, target -> initial.selectedPath == null && target.selectedPath != null },
            label = "gitMasterDetail",
        ) { state ->
            if (state.selectedPath != null) {
                DiffView(uiState = state, interactionListener = interactionListener, onOpenDiff = onOpenDiff)
            } else {
                ChangesView(state, interactionListener, onOpenConflicts)
            }
        }
    }
    if (uiState.authorDialogVisible) {
        GitAuthorDialog(uiState, interactionListener)
    }
    if (uiState.forcePushConfirmVisible) {
        AslDialog(
            title = "Force push with lease?",
            body = "This overwrites the remote branch only if it still matches your last fetched version. It never fetches first; a changed remote is rejected.",
            variant = AslDialogVariant.Confirm,
            confirmLabel = "Force push",
            cancelLabel = "Cancel",
            destructive = true,
            onDismiss = interactionListener::onDismissForcePush,
            onConfirm = interactionListener::onConfirmForcePush,
        )
    }
    if (uiState.remoteEditorVisible) RemoteEditorDialog(uiState, interactionListener)
    GitHubAuthDialog(uiState.authPrompt, interactionListener)
    if (uiState.bootstrapVisible) BootstrapDialog(uiState, interactionListener)
    if (uiState.abortConfirmVisible) {
        AslDialog(
            title = "Abort ${uiState.repositoryState.name.lowercase()}?",
            body = "This restores the working tree and branch to the state before the in-progress operation.",
            variant = AslDialogVariant.Confirm,
            confirmLabel = "Abort operation",
            cancelLabel = "Cancel",
            destructive = true,
            onDismiss = interactionListener::onDismissAbortOperation,
            onConfirm = interactionListener::onConfirmAbortOperation,
        )
    }
    if (uiState.pendingRestorePaths.isNotEmpty()) {
        AslDialog(
            title = "Rollback local changes?",
            body = "The following worktree changes will be discarded:\n${uiState.pendingRestorePaths.joinToString("\n")}",
            variant = AslDialogVariant.Confirm,
            confirmLabel = "Rollback",
            cancelLabel = "Cancel",
            destructive = true,
            onDismiss = interactionListener::onDismissRestore,
            onConfirm = interactionListener::onConfirmRestore,
        )
    }
    uiState.cleanPreview?.let { paths -> CleanDialog(uiState, paths, interactionListener) }
    uiState.pendingRemoteRemoval?.let { name ->
        AslDialog(
            title = "Remove remote '$name'?",
            body = "This removes the remote configuration. Local branches that reference it will keep broken upstream settings until reconfigured.",
            variant = AslDialogVariant.Confirm,
            confirmLabel = "Remove",
            cancelLabel = "Cancel",
            destructive = true,
            onDismiss = interactionListener::onDismissRemoveRemote,
            onConfirm = interactionListener::onConfirmRemoveRemote,
        )
    }
}

@Composable
private fun ChangesView(
    uiState: GitPanelUiState,
    interactionListener: GitPanelInteractionListener,
    onOpenConflicts: () -> Unit,
) {
    val colors = AslTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        GitChangesHeader(uiState, interactionListener)
        if (uiState.repositoryState != GitRepositoryState.SAFE) {
            OperationBanner(uiState, interactionListener)
        }
        GitChangedFileList(uiState = uiState, interactionListener = interactionListener, onOpenConflicts = onOpenConflicts, modifier = Modifier.weight(1f).fillMaxSize())
        HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
        GitCommitBox(
            uiState = uiState,
            interactionListener = interactionListener,
            modifier = Modifier.imePadding(),
        )
    }
}

@Composable
private fun GitChangesHeader(
    uiState: GitPanelUiState,
    interactionListener: GitPanelInteractionListener,
) {
    val colors = AslTheme.colors
    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null) {
            delay(4000)
            interactionListener.onStatusMessageShown()
        }
    }
    val statusText = uiState.operationLabel ?: uiState.statusMessage
    if (statusText != null || uiState.isBusy) {
        Column(modifier = Modifier.fillMaxWidth().padding(start = 12.dp, end = 8.dp, top = 6.dp)) {
            if (statusText != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    if (uiState.operationCancellable) {
                        AslIconButton(
                            icon = "x",
                            contentDescription = "Cancel Git operation",
                            onClick = interactionListener::onCancelOperation,
                            size = 32.dp,
                            iconSize = 16.dp,
                        )
                    }
                }
            }
            if (uiState.isBusy) {
                AslLinearProgress(value = uiState.operationProgress, modifier = Modifier.padding(top = 4.dp, end = 4.dp))
            }
        }
    }
    val changeCount = uiState.stagedChanges.size + uiState.unstagedChanges.size + uiState.untrackedChanges.size
    val hasChipRow = uiState.hasSelection || changeCount > 0 ||
        (uiState.behind ?: 0) > 0 || (uiState.ahead ?: 0) > 0
    if (hasChipRow) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (uiState.hasSelection) {
                AslChip(
                    label = "${uiState.selectionCount} selected",
                    kind = AslChipKind.Filter,
                    selected = true,
                )
                if (uiState.canStageSelection) {
                    AslChip(
                        label = "Stage",
                        icon = "plus",
                        kind = AslChipKind.Filter,
                        disabled = uiState.isBusy,
                        onClick = interactionListener::onStageSelected,
                    )
                }
                if (uiState.canUnstageSelection) {
                    AslChip(
                        label = "Unstage",
                        icon = "minus",
                        kind = AslChipKind.Filter,
                        disabled = uiState.isBusy,
                        onClick = interactionListener::onUnstageSelected,
                    )
                }
                if (uiState.canRevertSelection) {
                    AslChip(
                        label = "Revert",
                        icon = "rotate-ccw",
                        kind = AslChipKind.Filter,
                        disabled = uiState.isBusy,
                        onClick = interactionListener::onRevertSelected,
                    )
                }
                AslChip(
                    label = "Clear",
                    icon = "x",
                    kind = AslChipKind.Assist,
                    disabled = uiState.isBusy,
                    onClick = interactionListener::onClearSelection,
                )
            } else {
                if (changeCount > 0) {
                    AslChip(
                        label = if (changeCount == 1) "1 change" else "$changeCount changes",
                        kind = AslChipKind.Status,
                        status = AslChipStatus.Neutral,
                    )
                }
                uiState.behind?.takeIf { it > 0 }?.let {
                    AslChip(label = "↓$it", kind = AslChipKind.Status, status = AslChipStatus.Info)
                }
                uiState.ahead?.takeIf { it > 0 }?.let {
                    AslChip(label = "↑$it", kind = AslChipKind.Status, status = AslChipStatus.Success)
                }
                if (changeCount > 0) {
                    AslChip(
                        label = "Select",
                        icon = "circle-check",
                        kind = AslChipKind.Filter,
                        disabled = uiState.isBusy,
                        onClick = interactionListener::onSelectAllChanges,
                    )
                }
            }
        }
    }
    HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
}

@Composable
private fun GitActionsOverflowMenu(
    uiState: GitPanelUiState,
    interactionListener: GitPanelInteractionListener,
    onOpenHistory: () -> Unit,
    onOpenStashes: () -> Unit,
) {
    AslOverflowMenu(
        items = listOf(
            AslOverflowMenuEntry.Item("Push", icon = "upload", disabled = uiState.isBusy),
            AslOverflowMenuEntry.Item("Fetch", icon = "refresh-cw", disabled = uiState.isBusy),
            AslOverflowMenuEntry.Item("Pull (merge)", icon = "download", disabled = uiState.isBusy),
            AslOverflowMenuEntry.Item("Pull (rebase)", icon = "download", disabled = uiState.isBusy),
            AslOverflowMenuEntry.Divider,
            AslOverflowMenuEntry.Item("Git author", icon = "user", disabled = uiState.isBusy),
            AslOverflowMenuEntry.Item("Remotes", icon = "globe", disabled = uiState.isBusy),
            AslOverflowMenuEntry.Divider,
            AslOverflowMenuEntry.Item("Commit history", icon = "history", disabled = uiState.isBusy),
            AslOverflowMenuEntry.Item("Stashes", icon = "package", disabled = uiState.isBusy),
            AslOverflowMenuEntry.Divider,
            AslOverflowMenuEntry.Item("Clean untracked files", icon = "trash-2", disabled = uiState.isBusy, destructive = true),
            AslOverflowMenuEntry.Item("Force push (with lease)", icon = "upload", disabled = uiState.isBusy, destructive = true),
        ),
        onSelect = { item, _ ->
            when (item.label) {
                "Push" -> interactionListener.onPush()
                "Fetch" -> interactionListener.onFetch()
                "Pull (merge)" -> {
                    interactionListener.onPullModeChanged(PullMode.MERGE)
                    interactionListener.onPull()
                }
                "Pull (rebase)" -> {
                    interactionListener.onPullModeChanged(PullMode.REBASE)
                    interactionListener.onPull()
                }
                "Git author" -> interactionListener.onOpenAuthorDialog()
                "Remotes" -> interactionListener.onOpenRemotes()
                "Commit history" -> onOpenHistory()
                "Stashes" -> onOpenStashes()
                "Clean untracked files" -> interactionListener.onPreviewClean()
                else -> interactionListener.onRequestForcePush()
            }
        },
    )
}

@Composable
private fun SubmodulesView(
    uiState: GitPanelUiState,
    interactionListener: GitPanelInteractionListener,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "Submodule repositories are initialised and updated here; their inner commits are not managed.",
            style = MaterialTheme.typography.bodySmall,
            color = AslTheme.colors.textSecondary,
            modifier = Modifier.padding(12.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AslButton(
                label = "Init",
                onClick = interactionListener::onInitSubmodules,
                variant = AslButtonVariant.Secondary,
                disabled = uiState.isBusy,
                modifier = Modifier.weight(1f),
            )
            AslButton(
                label = "Update",
                onClick = interactionListener::onUpdateSubmodules,
                disabled = uiState.isBusy,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = AslTheme.colors.borderSubtle, modifier = Modifier.padding(top = 8.dp))
        when {
            uiState.submodulesLoading -> AslLinearProgress(label = "Loading submodules", modifier = Modifier.padding(16.dp))
            uiState.submodules.isEmpty() -> AslEmptyState(
                title = "No submodules",
                subtitle = "This repository has no configured submodules.",
                icon = "layers",
                modifier = Modifier.fillMaxSize(),
            )
            else -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                uiState.submodules.forEach { module ->
                    AslListItem(
                        title = "${module.name} · ${module.status.name.lowercase().replace('_', ' ')}",
                        subtitle = listOf(module.path, module.url)
                            .filter(String::isNotBlank)
                            .joinToString(" · ")
                            .middleEllipsis(),
                        icon = "layers",
                    )
                }
            }
        }
    }
}

@Composable
private fun BootstrapDialog(uiState: GitPanelUiState, interactionListener: GitPanelInteractionListener) {
    AslDialog(
        title = "Enable version control",
        body = "Initialise Git and add a standard Android .gitignore.",
        variant = AslDialogVariant.Input,
        confirmLabel = "Enable Git",
        cancelLabel = "Cancel",
        onDismiss = interactionListener::onDismissBootstrap,
        onConfirm = interactionListener::onConfirmBootstrap,
        inputContent = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AslSwitch(
                    checked = uiState.bootstrapInitialCommit,
                    onCheckedChange = interactionListener::onBootstrapInitialCommitChanged,
                    label = "Create initial commit",
                )
                if (uiState.bootstrapInitialCommit) {
                    AslTextField(
                        value = uiState.bootstrapMessage,
                        onValueChange = interactionListener::onBootstrapMessageChanged,
                        label = "Commit message",
                        placeholder = "Initial commit",
                    )
                }
            }
        },
    )
}

@Composable
private fun OperationBanner(uiState: GitPanelUiState, interactionListener: GitPanelInteractionListener) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "${uiState.repositoryState.name.lowercase().replaceFirstChar(Char::uppercase)} in progress",
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
        )
        if (uiState.repositoryState == GitRepositoryState.REBASING) {
            AslButton("Continue", interactionListener::onContinueOperation, variant = AslButtonVariant.Tertiary, disabled = uiState.isBusy)
        }
        if (uiState.repositoryState != GitRepositoryState.BISECTING) {
            AslButton("Abort", interactionListener::onRequestAbortOperation, variant = AslButtonVariant.Tertiary, disabled = uiState.isBusy)
        }
    }
}

@Composable
private fun CleanDialog(
    uiState: GitPanelUiState,
    paths: List<String>,
    interactionListener: GitPanelInteractionListener,
) {
    AslDialog(
        title = "Clean untracked files?",
        body = if (paths.isEmpty()) "Nothing would be removed." else
            "These paths will be permanently deleted:\n${paths.joinToString("\n")}",
        variant = AslDialogVariant.Confirm,
        confirmLabel = "Delete ${paths.size} path(s)",
        cancelLabel = "Cancel",
        destructive = true,
        onDismiss = interactionListener::onDismissClean,
        onConfirm = interactionListener::onConfirmClean,
        inputContent = {
            AslSwitch(
                checked = uiState.cleanIncludeIgnored,
                onCheckedChange = interactionListener::onCleanIncludeIgnoredChanged,
                label = "Include ignored files",
            )
        },
    )
}

@Composable
private fun GitChangedFileList(
    uiState: GitPanelUiState,
    interactionListener: GitPanelInteractionListener,
    onOpenConflicts: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AslStateCrossfade(
        targetState = when {
            uiState.loading -> GitListState.Loading
            uiState.hasChanges -> GitListState.Populated
            else -> GitListState.Empty
        },
        modifier = modifier,
        label = "gitChanges",
    ) { listState ->
        when (listState) {
            GitListState.Loading -> AslLinearProgress(
                label = "Loading repository status",
                modifier = Modifier.padding(16.dp),
            )
            GitListState.Empty -> AslEmptyState(
                icon = "git-commit",
                title = "No local changes",
                subtitle = "Edit files to see them appear here for commit.",
                modifier = Modifier.fillMaxSize(),
            )
            GitListState.Populated ->
                Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    GitConflictSection(uiState.conflicts, interactionListener, onOpenConflicts)
                    GitChangeSection("Staged", uiState.stagedChanges, GitDiffTarget.HEAD_TO_INDEX, uiState, interactionListener)
                    GitChangeSection("Changes", uiState.unstagedChanges, GitDiffTarget.INDEX_TO_WORKTREE, uiState, interactionListener)
                    GitChangeSection("Untracked", uiState.untrackedChanges, GitDiffTarget.INDEX_TO_WORKTREE, uiState, interactionListener)
                }
            }
    }
}

private enum class GitListState { Loading, Empty, Populated }

@Composable
private fun GitConflictSection(
    conflicts: List<GitChangeUiModel>,
    interactionListener: GitPanelInteractionListener,
    onOpenConflicts: () -> Unit,
) {
    if (conflicts.isEmpty()) return
    SectionHeader(title = "Conflicts", count = conflicts.size)
    conflicts.forEach { change ->
        AslListItem(
            title = change.displayPath.middleEllipsis(),
            subtitle = change.description,
            icon = "triangle-alert",
            iconColor = AslTheme.colors.error,
            trailing = { GitStatusBadge(change.status) },
            onClick = onOpenConflicts,
        )
    }
}

@Composable
private fun GitChangeSection(
    title: String,
    changes: List<GitChangeUiModel>,
    diffTarget: GitDiffTarget,
    uiState: GitPanelUiState,
    interactionListener: GitPanelInteractionListener,
) {
    if (changes.isEmpty()) return
    val paths = changes.map { it.path }
    val selectedInSection = paths.count { it in uiState.selectedPaths }
    val allSelected = selectedInSection == paths.size
    SectionHeader(
        title = title,
        count = changes.size,
        onToggleSection = if (uiState.isBusy) null else {
            { select -> interactionListener.onToggleSectionSelect(paths, select) }
        },
        sectionSelected = allSelected,
        sectionIndeterminate = selectedInSection in 1 until paths.size,
    )
    changes.forEach { change ->
        val checked = change.path in uiState.selectedPaths
        AslListItem(
            title = change.displayPath.middleEllipsis(),
            subtitle = change.description,
            icon = "file-code",
            selected = checked,
            leading = {
                AslCheckbox(
                    checked = checked,
                    onCheckedChange = { interactionListener.onToggleSelect(change.path) },
                    disabled = uiState.isBusy,
                )
            },
            trailing = { GitStatusBadge(change.status) },
            onClick = { interactionListener.onSelectChange(change.path, diffTarget) },
        )
    }
}

@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    onToggleSection: ((Boolean) -> Unit)? = null,
    sectionSelected: Boolean = false,
    sectionIndeterminate: Boolean = false,
) {
    val colors = AslTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onToggleSection != null) {
                    Modifier.clickable(enabled = true) {
                        if (sectionSelected && !sectionIndeterminate) onToggleSection(false)
                        else onToggleSection(true)
                    }
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (onToggleSection != null) {
            AslIcon(
                name = when {
                    sectionSelected && !sectionIndeterminate -> "circle-check"
                    sectionIndeterminate -> "minus"
                    else -> "square"
                },
                size = 16.dp,
                tint = if (sectionSelected || sectionIndeterminate) colors.accentPrimary else colors.textTertiary,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = colors.textSecondary,
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
            modifier = Modifier
                .background(colors.surfaceContainerLow, AslShape.xs)
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

@Composable
private fun GitCommitBox(
    uiState: GitPanelUiState,
    interactionListener: GitPanelInteractionListener,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AslTextField(
            value = uiState.commitMessage,
            onValueChange = { interactionListener.onCommitMessageChanged(it) },
            placeholder = "Commit message",
        )


        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AslButton(
                label = "Commit",
                onClick = { interactionListener.onCommit() },
                icon = "git-commit",
                modifier = Modifier.fillMaxWidth(),
                loading = uiState.isCommitting,
                disabled = !uiState.canCommit,
            )
            AslButton(
                label = "Commit & push",
                onClick = { interactionListener.onCommitAndPush() },
                icon = "upload",
                modifier = Modifier.fillMaxWidth(),
                variant = AslButtonVariant.Secondary,
                disabled = !uiState.canCommit,
            )
        }
    }
}

@Composable
private fun GitAuthorDialog(uiState: GitPanelUiState, interactionListener: GitPanelInteractionListener) {
    AslDialog(
        title = "Git author",
        variant = AslDialogVariant.Input,
        confirmLabel = "Save override",
        cancelLabel = "Cancel",
        onDismiss = interactionListener::onDismissAuthorDialog,
        onConfirm = interactionListener::onSaveLocalAuthor,
        inputContent = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                AslTextField(
                    value = uiState.authorName,
                    onValueChange = interactionListener::onAuthorNameChanged,
                    label = "Name",
                )
                AslTextField(
                    value = uiState.authorEmail,
                    onValueChange = interactionListener::onAuthorEmailChanged,
                    label = "Email",
                )
                AslButton(
                    label = "Use app default",
                    onClick = interactionListener::onUseAppAuthor,
                    variant = AslButtonVariant.Tertiary,
                )
            }
        },
    )
}

@Composable
private fun RemotesView(uiState: GitPanelUiState, interactionListener: GitPanelInteractionListener) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Configured remotes",
                style = MaterialTheme.typography.labelMedium,
                color = AslTheme.colors.textSecondary,
                modifier = Modifier.weight(1f),
            )
            AslButton(
                label = "Add",
                icon = "plus",
                onClick = interactionListener::onAddRemote,
                variant = AslButtonVariant.Tertiary,
                disabled = uiState.isBusy,
            )
        }
        HorizontalDivider(color = AslTheme.colors.borderSubtle)
        when {
            uiState.remotesLoading -> AslLinearProgress(
                label = "Loading remotes",
                modifier = Modifier.padding(16.dp),
            )
            uiState.remotes.isEmpty() -> AslEmptyState(
                icon = "globe",
                title = "No remotes",
                subtitle = "Add an HTTP or file remote to fetch and push.",
                modifier = Modifier.fillMaxSize(),
            )
            else -> Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                uiState.remotes.forEach { remote ->
                    AslListItem(
                        title = remote.name,
                        subtitle = remote.url.middleEllipsis(),
                        icon = "globe",
                        trailing = {
                            Row {
                                AslIconButton(
                                    icon = "edit-2",
                                    contentDescription = "Edit ${remote.name}",
                                    onClick = { interactionListener.onEditRemote(remote.name) },
                                    size = 28.dp,
                                    iconSize = 14.dp,
                                    disabled = uiState.isBusy,
                                )
                                AslIconButton(
                                    icon = "trash-2",
                                    contentDescription = "Remove ${remote.name}",
                                    onClick = { interactionListener.onRequestRemoveRemote(remote.name) },
                                    size = 28.dp,
                                    iconSize = 14.dp,
                                    disabled = uiState.isBusy,
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun RemoteEditorDialog(uiState: GitPanelUiState, interactionListener: GitPanelInteractionListener) {
    val editing = uiState.editingRemoteName
    AslDialog(
        title = if (editing == null) "Add remote" else "Edit remote '${editing}'",
        variant = AslDialogVariant.Input,
        confirmLabel = "Save",
        cancelLabel = "Cancel",
        onDismiss = interactionListener::onDismissRemoteEditor,
        onConfirm = interactionListener::onSaveRemote,
        inputContent = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {


                AslTextField(
                    value = uiState.remoteUrl,
                    onValueChange = interactionListener::onRemoteUrlChanged,
                    label = "Repository URL",
                    placeholder = "https://github.com/owner/repo.git",
                    helper = if (editing == null) "Added as the 'origin' remote." else null,
                    error = uiState.remoteUrlError,
                )
            }
        },
    )
}

@Composable
private fun DiffView(
    uiState: GitPanelUiState,
    interactionListener: GitPanelInteractionListener,
    onOpenDiff: (String, GitDiffTarget) -> Unit,
) {
    val colors = AslTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AslIconButton(icon = "arrow-left", contentDescription = "Back", onClick = { interactionListener.onCloseDiff() })
            Text(
                text = uiState.selectedPath.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AslButton(
                label = "Open diff",
                variant = AslButtonVariant.Tertiary,
                onClick = { onOpenDiff(uiState.selectedPath.orEmpty(), uiState.selectedDiffTarget) },
            )
        }
        HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)


        val vScroll = rememberScrollState()
        val hScroll = rememberScrollState()
        Column(modifier = Modifier.weight(1f).verticalScroll(vScroll)) {
            Column(modifier = Modifier.horizontalScroll(hScroll).width(IntrinsicSize.Max)) {
                uiState.diffLines.forEach { line ->
                    AslDiffLine(
                        kind = line.kind,
                        text = line.text,
                        oldNo = line.oldNo,
                        newNo = line.newNo,
                        noWrap = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun GitStatusBadge(status: GitFileStatus) {
    val (label, chipStatus) = when (status) {
        GitFileStatus.MODIFIED -> "M" to AslChipStatus.Info
        GitFileStatus.ADDED -> "A" to AslChipStatus.Success
        GitFileStatus.DELETED -> "D" to AslChipStatus.Error
        GitFileStatus.UNTRACKED -> "U" to AslChipStatus.Warning
        GitFileStatus.CONFLICTED -> "!" to AslChipStatus.Error
    }
    AslChip(label = label, kind = AslChipKind.Status, status = chipStatus)
}
