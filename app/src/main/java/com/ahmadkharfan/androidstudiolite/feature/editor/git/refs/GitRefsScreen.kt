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
import androidx.compose.ui.platform.LocalUriHandler
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    GitRefsScreen(state, viewModel, onBack)
}

@Composable
private fun GitRefsScreen(state: GitRefsUiState, viewModel: GitRefsViewModel, onBack: () -> Unit) {
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
    val title = when (state.mode) {
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
                    if (state.mode == GitRefsMode.TAGS && state.tags.isNotEmpty()) {
                        AslButton(
                            label = "Push all",
                            onClick = viewModel::pushAllTags,
                            variant = AslButtonVariant.Tertiary,
                            disabled = state.loading,
                        )
                    }
                    AslButton(
                        label = when (state.mode) {
                            GitRefsMode.STASHES -> "Stash changes"
                            GitRefsMode.BRANCHES -> "New branch"
                            GitRefsMode.TAGS -> "New"
                        },
                        onClick = { name = ""; message = ""; createOpen = true },
                        variant = AslButtonVariant.Tertiary,
                        disabled = state.loading,
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.error != null) {
                Text(
                    state.error,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            if (state.loading) AslLinearProgress(label = "Updating $title", modifier = Modifier.padding(12.dp))
            when (state.mode) {
                GitRefsMode.BRANCHES -> BranchList(
                    state = state,
                    viewModel = viewModel,
                    onRename = { branch -> name = branch.name; rename = branch },
                    onDelete = { deleteBranch = it },
                    onMerge = { mergeBranch = it },
                )
                GitRefsMode.TAGS -> TagList(state.tags, viewModel, { deleteTag = it })
                GitRefsMode.STASHES -> StashList(state.stashes, viewModel, { popStash = it }, { dropStash = it })
            }
        }
    }

    if (createOpen) {
        AslDialog(
            title = when (state.mode) {
                GitRefsMode.BRANCHES -> "Create branch"
                GitRefsMode.TAGS -> "Create tag"
                GitRefsMode.STASHES -> "Stash changes"
            },
            variant = AslDialogVariant.Input,
            confirmLabel = "Create",
            cancelLabel = "Cancel",
            onDismiss = { createOpen = false },
            onConfirm = {
                when (state.mode) {
                    GitRefsMode.BRANCHES -> viewModel.createBranch(name)
                    GitRefsMode.TAGS -> viewModel.createTag(name, message)
                    GitRefsMode.STASHES -> viewModel.createStash(message, includeUntracked)
                }
                createOpen = false
            },
            inputContent = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.mode != GitRefsMode.STASHES) AslTextField(name, { name = it }, label = "Name")
                    if (state.mode != GitRefsMode.BRANCHES) AslTextField(message, { message = it }, label = "Message")
                    if (state.mode == GitRefsMode.STASHES) {
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
            onConfirm = { viewModel.renameBranch(branch.name, name); rename = null },
            inputContent = { AslTextField(name, { name = it }, label = "New name") },
        )
    }
    deleteBranch?.let { branch ->
        ConfirmDelete(
            title = "Delete ${branch.name}?",
            body = "The branch ref will be removed. Commits not reachable elsewhere may eventually be lost.",
            confirm = { viewModel.deleteBranch(branch.name); deleteBranch = null },
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
            onConfirm = { viewModel.merge(branch.name); mergeBranch = null },
        )
    }
    state.forceDeleteCandidate?.let { branch ->
        ConfirmDelete(
            title = "Force delete $branch?",
            body = "This branch contains commits not merged into the current branch.",
            confirm = { viewModel.deleteBranch(branch, force = true) },
            dismiss = viewModel::dismissForceDelete,
        )
    }
    if (state.authPromptVisible) {
        val uriHandler = LocalUriHandler.current
        val host = state.authPromptHost ?: "the remote"
        AslDialog(
            title = "Sign in to $host",
            variant = AslDialogVariant.Input,
            confirmLabel = "Save & retry",
            cancelLabel = "Cancel",
            onDismiss = viewModel::dismissAuthPrompt,
            onConfirm = viewModel::submitAuthToken,
            inputContent = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Pushing to $host needs a GitHub personal access token with repository write " +
                            "access. It's stored securely and reused for future push/pull.",
                        style = MaterialTheme.typography.bodySmall,
                        color = AslTheme.colors.textSecondary,
                    )
                    AslTextField(
                        value = state.authPromptToken,
                        onValueChange = viewModel::onAuthTokenChanged,
                        label = "Access token",
                        placeholder = "ghp_…",
                        type = com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextFieldType.Password,
                    )
                    AslButton(
                        label = "Create a token on GitHub",
                        onClick = { uriHandler.openUri("https://github.com/settings/tokens/new?scopes=repo&description=Android%20Studio%20Lite") },
                        icon = "external-link",
                        variant = AslButtonVariant.Tertiary,
                    )
                }
            },
        )
    }
    deleteTag?.let { tag ->
        ConfirmDelete(
            title = "Delete tag ${tag.name}?",
            body = "This deletes the local tag. A separately-pushed remote tag is unchanged.",
            confirm = { viewModel.deleteTag(tag.name); deleteTag = null },
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
            onConfirm = { viewModel.popStash(stash.index); popStash = null },
        )
    }
    dropStash?.let { stash ->
        ConfirmDelete(
            title = "Drop stash@{${stash.index}}?",
            body = "This permanently removes the stashed changes.",
            confirm = { viewModel.dropStash(stash.index); dropStash = null },
            dismiss = { dropStash = null },
        )
    }
}

@Composable
private fun BranchList(
    state: GitRefsUiState,
    viewModel: GitRefsViewModel,
    onRename: (GitBranch) -> Unit,
    onDelete: (GitBranch) -> Unit,
    onMerge: (GitBranch) -> Unit,
) {
    val colors = AslTheme.colors
    var query by remember { mutableStateOf("") }

    LaunchedEffect(state.syncMessage) {
        if (state.syncMessage != null) {
            delay(4000)
            viewModel.dismissSyncMessage()
        }
    }

    Column(Modifier.fillMaxSize()) {
        // Fetch/pull/push for the current branch live here (not the compact Changes toolbar), plus
        // ahead/behind at a glance — this screen is the one-stop place for branch + sync actions.
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
                AslIconButton(icon = "refresh-cw", contentDescription = "Fetch", onClick = viewModel::fetch, size = 32.dp, iconSize = 16.dp, disabled = state.syncing)
                AslIconButton(icon = "download", contentDescription = "Pull (merge)", onClick = { viewModel.pull(PullMode.MERGE) }, size = 32.dp, iconSize = 16.dp, disabled = state.syncing)
                AslIconButton(icon = "upload", contentDescription = "Push", onClick = viewModel::push, size = 32.dp, iconSize = 16.dp, disabled = state.syncing)
            }
            if (state.syncing) AslLinearProgress(modifier = Modifier.padding(top = 6.dp))
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
                            "Publish" -> viewModel.publish(branch.name)
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
                            "Checkout" -> viewModel.checkout(branch)
                            "Merge into current" -> onMerge(branch)
                            "Publish" -> viewModel.publish(branch.name)
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
                    ) { label -> if (label == "Checkout") viewModel.checkout(branch) }
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

/** One branch row: name + current/ahead badge, and a kebab menu instead of an always-visible button
 *  row — keeps rows compact and matches Android Studio's "select, then act" branch list. */
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
private fun TagList(tags: List<GitTag>, viewModel: GitRefsViewModel, onDelete: (GitTag) -> Unit) {
    if (tags.isEmpty()) return AslEmptyState("No tags", modifier = Modifier.fillMaxSize(), icon = "tag")
    LazyColumn(Modifier.fillMaxSize()) {
        items(tags, key = { it.name }) { tag ->
            RefRow(tag.name, if (tag.annotated) tag.message ?: "Annotated" else "Lightweight") {
                AslButton("Push", { viewModel.pushTag(tag.name) }, variant = AslButtonVariant.Tertiary)
                AslButton("Delete", { onDelete(tag) }, variant = AslButtonVariant.Tertiary)
            }
        }
    }
}

@Composable
private fun StashList(
    stashes: List<GitStash>,
    viewModel: GitRefsViewModel,
    onPop: (GitStash) -> Unit,
    onDrop: (GitStash) -> Unit,
) {
    if (stashes.isEmpty()) return AslEmptyState("No stashes", modifier = Modifier.fillMaxSize(), icon = "package")
    LazyColumn(Modifier.fillMaxSize()) {
        items(stashes, key = { it.id }) { stash ->
            RefRow("stash@{${stash.index}}", stash.message) {
                AslButton("Apply", { viewModel.applyStash(stash.index) }, variant = AslButtonVariant.Tertiary)
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
