package com.ahmadkharfan.androidstudiolite.feature.editor.git.refs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslOverflowMenu
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslOverflowMenuEntry
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslEmptyState
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialog
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialogVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChip
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChipKind
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChipStatus
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslCheckbox
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitHubAuthDialog
import com.ahmadkharfan.androidstudiolite.domain.model.GitBranch
import com.ahmadkharfan.androidstudiolite.domain.model.GitStash
import com.ahmadkharfan.androidstudiolite.domain.model.GitTag
import com.ahmadkharfan.androidstudiolite.domain.model.PullMode
import com.ahmadkharfan.androidstudiolite.feature.git.middleEllipsis
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun GitRefsRoute(
    projectId: String,
    mode: GitRefsMode,
    onBack: () -> Unit,
    viewModel: GitRefsViewModel = koinViewModel { parametersOf(projectId, mode) },
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    GitRefsScreen(uiState = uiState, interactionListener = viewModel, onBack = onBack)
}

@Composable
private fun GitRefsScreen(
    uiState: GitRefsUiState,
    interactionListener: GitRefsInteractionListener,
    onBack: () -> Unit,
) {
    var createOpen by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var includeUntracked by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<GitBranch?>(null) }
    var deleteBranch by remember { mutableStateOf<GitBranch?>(null) }
    var mergeBranch by remember { mutableStateOf<GitBranch?>(null) }
    var deleteTag by remember { mutableStateOf<GitTag?>(null) }
    var dropStash by remember { mutableStateOf<GitStash?>(null) }
    var popStash by remember { mutableStateOf<GitStash?>(null) }
    val title = when (uiState.mode) {
        GitRefsMode.BRANCHES -> "Branches"
        GitRefsMode.TAGS -> "Tags"
        GitRefsMode.STASHES -> "Stashes"
    }
    Scaffold(
        topBar = {
            AslTopAppBar(
                title = title,
                onBack = onBack,
                applyStatusBarInset = true,
                actions = {
                    if (uiState.mode == GitRefsMode.TAGS && uiState.tags.isNotEmpty()) {
                        AslButton(
                            label = "Push all",
                            onClick = interactionListener::pushAllTags,
                            variant = AslButtonVariant.Tertiary,
                            disabled = uiState.loading,
                        )
                    }
                    AslButton(
                        label = when (uiState.mode) {
                            GitRefsMode.STASHES -> "Stash changes"
                            GitRefsMode.BRANCHES -> "New branch"
                            GitRefsMode.TAGS -> "New"
                        },
                        onClick = { name = ""; message = ""; createOpen = true },
                        variant = AslButtonVariant.Tertiary,
                        disabled = uiState.loading,
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (uiState.error != null) {
                Text(
                    uiState.error,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (uiState.loading) AslLinearProgress(label = "Updating $title", modifier = Modifier.padding(12.dp))
            when (uiState.mode) {
                GitRefsMode.BRANCHES -> BranchList(
                    state = uiState,
                    interactionListener = interactionListener,
                    onRename = { branch -> name = branch.name; rename = branch },
                    onDelete = { deleteBranch = it },
                    onMerge = { mergeBranch = it },
                )
                GitRefsMode.TAGS -> TagList(uiState.tags, interactionListener, { deleteTag = it })
                GitRefsMode.STASHES -> StashList(uiState.stashes, interactionListener, { popStash = it }, { dropStash = it })
            }
        }
    }

    if (createOpen) {
        AslDialog(
            title = when (uiState.mode) {
                GitRefsMode.BRANCHES -> "Create branch"
                GitRefsMode.TAGS -> "Create tag"
                GitRefsMode.STASHES -> "Stash changes"
            },
            variant = AslDialogVariant.Input,
            confirmLabel = "Create",
            cancelLabel = "Cancel",
            onDismiss = { createOpen = false },
            onConfirm = {
                when (uiState.mode) {
                    GitRefsMode.BRANCHES -> interactionListener.createBranch(name)
                    GitRefsMode.TAGS -> interactionListener.createTag(name, message)
                    GitRefsMode.STASHES -> interactionListener.createStash(message, includeUntracked)
                }
                createOpen = false
            },
            inputContent = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (uiState.mode != GitRefsMode.STASHES) AslTextField(name, { name = it }, label = "Name")
                    if (uiState.mode != GitRefsMode.BRANCHES) AslTextField(message, { message = it }, label = "Message")
                    if (uiState.mode == GitRefsMode.STASHES) {
                        AslCheckbox(includeUntracked, { includeUntracked = it }, label = "Include untracked files")
                    }
                }
            },
        )
    }
    rename?.let { branch ->
        AslDialog(
            title = "Rename ${branch.name}",
            variant = AslDialogVariant.Input,
            confirmLabel = "Rename",
            cancelLabel = "Cancel",
            onDismiss = { rename = null },
            onConfirm = { interactionListener.renameBranch(branch.name, name); rename = null },
            inputContent = { AslTextField(name, { name = it }, label = "New name") },
        )
    }
    deleteBranch?.let { branch ->
        ConfirmDelete(
            title = "Delete ${branch.name}?",
            body = "The branch ref will be removed. Commits not reachable elsewhere may eventually be lost.",
            confirm = { interactionListener.deleteBranch(branch.name); deleteBranch = null },
            dismiss = { deleteBranch = null },
        )
    }
    mergeBranch?.let { branch ->
        AslDialog(
            title = "Merge ${branch.name} into current branch?",
            body = "This merges ${branch.name} into the checked-out branch. If the changes conflict, the files appear in the Changes panel for you to resolve.",
            variant = AslDialogVariant.Confirm,
            confirmLabel = "Merge",
            cancelLabel = "Cancel",
            onDismiss = { mergeBranch = null },
            onConfirm = { interactionListener.merge(branch.name); mergeBranch = null },
        )
    }
    uiState.forceDeleteCandidate?.let { branch ->
        ConfirmDelete(
            title = "Force delete $branch?",
            body = "This branch contains commits not merged into the current branch.",
            confirm = { interactionListener.deleteBranch(branch, force = true) },
            dismiss = interactionListener::dismissForceDelete,
        )
    }
    GitHubAuthDialog(uiState.authPrompt, interactionListener)
    deleteTag?.let { tag ->
        ConfirmDelete(
            title = "Delete tag ${tag.name}?",
            body = "This deletes the local tag. A separately-pushed remote tag is unchanged.",
            confirm = { interactionListener.deleteTag(tag.name); deleteTag = null },
            dismiss = { deleteTag = null },
        )
    }
    popStash?.let { stash ->
        AslDialog(
            title = "Pop stash@{${stash.index}}?",
            body = "The stash is applied and dropped only when apply succeeds.",
            variant = AslDialogVariant.Confirm,
            confirmLabel = "Pop",
            cancelLabel = "Cancel",
            onDismiss = { popStash = null },
            onConfirm = { interactionListener.popStash(stash.index); popStash = null },
        )
    }
    dropStash?.let { stash ->
        ConfirmDelete(
            title = "Drop stash@{${stash.index}}?",
            body = "This permanently removes the stashed changes.",
            confirm = { interactionListener.dropStash(stash.index); dropStash = null },
            dismiss = { dropStash = null },
        )
    }
}

@Composable
private fun BranchList(
    state: GitRefsUiState,
    interactionListener: GitRefsInteractionListener,
    onRename: (GitBranch) -> Unit,
    onDelete: (GitBranch) -> Unit,
    onMerge: (GitBranch) -> Unit,
) {
    val colors = AslTheme.colors
    var query by remember { mutableStateOf("") }

    LaunchedEffect(state.syncMessage) {
        if (state.syncMessage != null) {
            delay(4000)
            interactionListener.dismissSyncMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {


        Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                state.behind?.takeIf { it > 0 }?.let { AslChip(label = "↓$it", kind = AslChipKind.Status, status = AslChipStatus.Info) }
                state.ahead?.takeIf { it > 0 }?.let { AslChip(label = "↑$it", kind = AslChipKind.Status, status = AslChipStatus.Success) }
                Text(
                    text = state.syncMessage.orEmpty(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                AslIconButton(icon = "refresh-cw", contentDescription = "Fetch", onClick = interactionListener::fetch, size = 32.dp, iconSize = 16.dp, disabled = state.isSyncing)
                AslIconButton(icon = "download", contentDescription = "Pull (merge)", onClick = { interactionListener.pull(PullMode.MERGE) }, size = 32.dp, iconSize = 16.dp, disabled = state.isSyncing)
                AslIconButton(icon = "upload", contentDescription = "Push", onClick = interactionListener::push, size = 32.dp, iconSize = 16.dp, disabled = state.isSyncing)
            }
            if (state.isSyncing) AslLinearProgress(modifier = Modifier.padding(top = 6.dp))
            AslTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = "Search branches",
                leadingIcon = "search",
                trailingIcon = "x".takeIf { query.isNotEmpty() },
                onTrailingClick = { query = "" },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        HorizontalDivider(color = colors.borderSubtle)

        val filtered = state.branches.filter { it.name.contains(query, ignoreCase = true) }
        if (filtered.isEmpty()) {
            AslEmptyState(
                title = if (query.isBlank()) "No branches" else "No branches match \"$query\"",
                modifier = Modifier.fillMaxSize(),
                icon = "git-branch",
            )
            return
        }
        LazyColumn(Modifier.fillMaxSize()) {
            val current = filtered.filter { it.current }
            val local = filtered.filter { !it.isRemote && !it.current }
            val remote = filtered.filter { it.isRemote }
            if (current.isNotEmpty()) {
                item { SectionLabel("Current") }
                items(current, key = { "current:${it.name}" }) { branch ->
                    BranchActionRow(
                        branch = branch,
                        entries = listOf(
                            AslOverflowMenuEntry.Item("Publish", icon = "upload"),
                            AslOverflowMenuEntry.Item("Rename", icon = "pencil"),
                        ),
                    ) { label ->
                        when (label) {
                            "Publish" -> interactionListener.publish(branch.name)
                            "Rename" -> onRename(branch)
                        }
                    }
                }
            }
            if (local.isNotEmpty()) {
                item { SectionLabel("Local") }
                items(local, key = { "local:${it.name}" }) { branch ->
                    BranchActionRow(
                        branch = branch,
                        entries = listOf(
                            AslOverflowMenuEntry.Item("Checkout", icon = "git-branch"),
                            AslOverflowMenuEntry.Item("Merge into current", icon = "sync"),
                            AslOverflowMenuEntry.Item("Publish", icon = "upload"),
                            AslOverflowMenuEntry.Item("Rename", icon = "pencil"),
                            AslOverflowMenuEntry.Divider,
                            AslOverflowMenuEntry.Item("Delete", icon = "trash-2", destructive = true),
                        ),
                    ) { label ->
                        when (label) {
                            "Checkout" -> interactionListener.checkout(branch)
                            "Merge into current" -> onMerge(branch)
                            "Publish" -> interactionListener.publish(branch.name)
                            "Rename" -> onRename(branch)
                            "Delete" -> onDelete(branch)
                        }
                    }
                }
            }
            if (remote.isNotEmpty()) {
                item { SectionLabel("Remote") }
                items(remote, key = { "remote:${it.name}" }) { branch ->
                    BranchActionRow(
                        branch = branch,
                        entries = listOf(AslOverflowMenuEntry.Item("Checkout", icon = "git-branch")),
                    ) { label -> if (label == "Checkout") interactionListener.checkout(branch) }
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = AslTheme.colors.textTertiary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun BranchActionRow(
    branch: GitBranch,
    entries: List<AslOverflowMenuEntry>,
    onSelect: (String) -> Unit,
) {
    AslListItem(
        title = branch.name.middleEllipsis(),
        subtitle = if (branch.current) "Checked out" else null,
        icon = if (branch.current) "check" else "git-branch",
        iconColor = if (branch.current) AslTheme.colors.success else null,
        trailing = {
            AslOverflowMenu(items = entries, onSelect = { item, _ -> onSelect(item.label) })
        },
    )
}

@Composable
private fun TagList(tags: List<GitTag>, interactionListener: GitRefsInteractionListener, onDelete: (GitTag) -> Unit) {
    if (tags.isEmpty()) return AslEmptyState("No tags", modifier = Modifier.fillMaxSize(), icon = "tag")
    LazyColumn(Modifier.fillMaxSize()) {
        items(tags, key = { it.name }) { tag ->
            RefRow(tag.name, if (tag.annotated) tag.message ?: "Annotated" else "Lightweight") {
                AslButton("Push", { interactionListener.pushTag(tag.name) }, variant = AslButtonVariant.Tertiary)
                AslButton("Delete", { onDelete(tag) }, variant = AslButtonVariant.Tertiary)
            }
        }
    }
}

@Composable
private fun StashList(
    stashes: List<GitStash>,
    interactionListener: GitRefsInteractionListener,
    onPop: (GitStash) -> Unit,
    onDrop: (GitStash) -> Unit,
) {
    if (stashes.isEmpty()) return AslEmptyState("No stashes", modifier = Modifier.fillMaxSize(), icon = "package")
    LazyColumn(Modifier.fillMaxSize()) {
        items(stashes, key = { it.id }) { stash ->
            RefRow("stash@{${stash.index}}", stash.message) {
                AslButton("Apply", { interactionListener.applyStash(stash.index) }, variant = AslButtonVariant.Tertiary)
                AslButton("Pop", { onPop(stash) }, variant = AslButtonVariant.Tertiary)
                AslButton("Drop", { onDrop(stash) }, variant = AslButtonVariant.Tertiary)
            }
        }
    }
}

@Composable
private fun RefRow(name: String, detail: String?, actions: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.titleSmall)
        detail?.let { Text(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
    }
    HorizontalDivider()
}

@Composable
private fun ConfirmDelete(title: String, body: String, confirm: () -> Unit, dismiss: () -> Unit) {
    AslDialog(
        title = title,
        body = body,
        variant = AslDialogVariant.Confirm,
        confirmLabel = "Delete",
        cancelLabel = "Cancel",
        destructive = true,
        onDismiss = dismiss,
        onConfirm = confirm,
    )
}
