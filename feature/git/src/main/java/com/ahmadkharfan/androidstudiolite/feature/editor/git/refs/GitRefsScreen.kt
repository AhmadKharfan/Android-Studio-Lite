package com.ahmadkharfan.androidstudiolite.feature.editor.git.refs

import kotlin.time.Duration.Companion.seconds
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.core.format.middleEllipsis
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
import com.ahmadkharfan.androidstudiolite.feature.editor.git.git.GitHubAuthDialog
import com.ahmadkharfan.androidstudiolite.domain.model.GitBranch
import com.ahmadkharfan.androidstudiolite.domain.model.GitStash
import com.ahmadkharfan.androidstudiolite.domain.model.GitTag
import com.ahmadkharfan.androidstudiolite.domain.model.PullMode
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.ahmadkharfan.androidstudiolite.core.common.R as CommonR
import com.ahmadkharfan.androidstudiolite.feature.git.R

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
        GitRefsMode.BRANCHES -> stringResource(R.string.git_refs_branches)
        GitRefsMode.TAGS -> stringResource(R.string.git_refs_tags)
        GitRefsMode.STASHES -> stringResource(R.string.git_refs_stashes)
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
                            label = stringResource(R.string.git_refs_push_all),
                            onClick = interactionListener::pushAllTags,
                            variant = AslButtonVariant.Tertiary,
                            disabled = uiState.loading,
                        )
                    }
                    AslButton(
                        label = when (uiState.mode) {
                            GitRefsMode.STASHES -> stringResource(R.string.git_refs_stash_changes)
                            GitRefsMode.BRANCHES -> stringResource(R.string.git_refs_new_branch)
                            GitRefsMode.TAGS -> stringResource(R.string.git_refs_new)
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
            if (uiState.loading) AslLinearProgress(label = stringResource(R.string.git_refs_updating, title), modifier = Modifier.padding(12.dp))
            when (uiState.mode) {
                GitRefsMode.BRANCHES -> BranchList(
                    state = uiState,
                    interactionListener = interactionListener,
                    onRename = { branch -> name = branch.name; rename = branch },
                    onDelete = { deleteBranch = it },
                    onMerge = { mergeBranch = it },
                )
                GitRefsMode.TAGS -> TagList(uiState.tags, interactionListener) { deleteTag = it }
                GitRefsMode.STASHES -> StashList(
                    uiState.stashes,
                    interactionListener,
                    onPop = { popStash = it },
                ) { dropStash = it }
            }
        }
    }

    if (createOpen) {
        AslDialog(
            title = when (uiState.mode) {
                GitRefsMode.BRANCHES -> stringResource(R.string.git_refs_create_branch)
                GitRefsMode.TAGS -> stringResource(R.string.git_refs_create_tag)
                GitRefsMode.STASHES -> stringResource(R.string.git_refs_stash_changes)
            },
            variant = AslDialogVariant.Input,
            confirmLabel = stringResource(CommonR.string.action_create),
            cancelLabel = stringResource(CommonR.string.action_cancel),
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
                    if (uiState.mode != GitRefsMode.STASHES) AslTextField(name, { name = it }, label = stringResource(R.string.git_refs_name))
                    if (uiState.mode != GitRefsMode.BRANCHES) AslTextField(message, { message = it }, label = stringResource(R.string.git_refs_message))
                    if (uiState.mode == GitRefsMode.STASHES) {
                        AslCheckbox(includeUntracked, { includeUntracked = it }, label = stringResource(R.string.git_refs_include_untracked))
                    }
                }
            },
        )
    }
    rename?.let { branch ->
        AslDialog(
            title = stringResource(R.string.git_refs_rename_title, branch.name),
            variant = AslDialogVariant.Input,
            confirmLabel = stringResource(CommonR.string.action_rename),
            cancelLabel = stringResource(CommonR.string.action_cancel),
            onDismiss = { rename = null },
            onConfirm = { interactionListener.renameBranch(branch.name, name); rename = null },
            inputContent = { AslTextField(name, { name = it }, label = stringResource(R.string.git_refs_new_name)) },
        )
    }
    deleteBranch?.let { branch ->
        ConfirmDelete(
            title = stringResource(R.string.git_refs_delete_branch_title, branch.name),
            body = stringResource(R.string.git_refs_delete_branch_body),
            confirm = { interactionListener.deleteBranch(branch.name); deleteBranch = null },
            dismiss = { deleteBranch = null },
        )
    }
    mergeBranch?.let { branch ->
        AslDialog(
            title = stringResource(R.string.git_refs_merge_title, branch.name),
            body = stringResource(R.string.git_refs_merge_body, branch.name),
            variant = AslDialogVariant.Confirm,
            confirmLabel = stringResource(R.string.git_refs_merge),
            cancelLabel = stringResource(CommonR.string.action_cancel),
            onDismiss = { mergeBranch = null },
            onConfirm = { interactionListener.merge(branch.name); mergeBranch = null },
        )
    }
    uiState.forceDeleteCandidate?.let { branch ->
        ConfirmDelete(
            title = stringResource(R.string.git_refs_force_delete_title, branch),
            body = stringResource(R.string.git_refs_force_delete_body),
            confirm = { interactionListener.deleteBranch(branch, force = true) },
            dismiss = interactionListener::dismissForceDelete,
        )
    }
    GitHubAuthDialog(uiState.authPrompt, interactionListener)
    deleteTag?.let { tag ->
        ConfirmDelete(
            title = stringResource(R.string.git_refs_delete_tag_title, tag.name),
            body = stringResource(R.string.git_refs_delete_tag_body),
            confirm = { interactionListener.deleteTag(tag.name); deleteTag = null },
            dismiss = { deleteTag = null },
        )
    }
    popStash?.let { stash ->
        AslDialog(
            title = stringResource(R.string.git_refs_pop_stash_title, stash.index),
            body = stringResource(R.string.git_refs_pop_stash_body),
            variant = AslDialogVariant.Confirm,
            confirmLabel = stringResource(R.string.git_refs_pop),
            cancelLabel = stringResource(CommonR.string.action_cancel),
            onDismiss = { popStash = null },
            onConfirm = { interactionListener.popStash(stash.index); popStash = null },
        )
    }
    dropStash?.let { stash ->
        ConfirmDelete(
            title = stringResource(R.string.git_refs_drop_stash_title, stash.index),
            body = stringResource(R.string.git_refs_drop_stash_body),
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
            delay(4.seconds)
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
                AslIconButton(icon = "refresh-cw", contentDescription = stringResource(R.string.git_action_fetch), onClick = interactionListener::fetch, size = 32.dp, iconSize = 16.dp, disabled = state.isSyncing)
                AslIconButton(icon = "download", contentDescription = stringResource(R.string.git_refs_pull_merge), onClick = { interactionListener.pull(PullMode.MERGE) }, size = 32.dp, iconSize = 16.dp, disabled = state.isSyncing)
                AslIconButton(icon = "upload", contentDescription = stringResource(R.string.git_action_push), onClick = interactionListener::push, size = 32.dp, iconSize = 16.dp, disabled = state.isSyncing)
            }
            if (state.isSyncing) AslLinearProgress(modifier = Modifier.padding(top = 6.dp))
            AslTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = stringResource(R.string.git_refs_search_branches),
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
                title = if (query.isBlank()) stringResource(R.string.git_refs_no_branches) else stringResource(R.string.git_refs_no_branches_match, query),
                modifier = Modifier.fillMaxSize(),
                icon = "git-branch",
            )
            return
        }
        val publish = stringResource(R.string.git_refs_publish)
        val rename = stringResource(CommonR.string.action_rename)
        val checkout = stringResource(R.string.git_refs_checkout)
        val mergeCurrent = stringResource(R.string.git_refs_merge_current)
        val delete = stringResource(CommonR.string.action_delete)
        LazyColumn(Modifier.fillMaxSize()) {
            val current = filtered.filter { it.current }
            val local = filtered.filter { !it.isRemote && !it.current }
            val remote = filtered.filter { it.isRemote }
            if (current.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.git_refs_current)) }
                items(current, key = { "current:${it.name}" }) { branch ->
                    BranchActionRow(
                        branch = branch,
                        entries = listOf(
                            AslOverflowMenuEntry.Item(publish, icon = "upload") {
                                interactionListener.publish(branch.name)
                            },
                            AslOverflowMenuEntry.Item(rename, icon = "pencil") {
                                onRename(branch)
                            },
                        ),
                    )
                }
            }
            if (local.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.git_refs_local)) }
                items(local, key = { "local:${it.name}" }) { branch ->
                    BranchActionRow(
                        branch = branch,
                        entries = listOf(
                            AslOverflowMenuEntry.Item(checkout, icon = "git-branch") {
                                interactionListener.checkout(branch)
                            },
                            AslOverflowMenuEntry.Item(mergeCurrent, icon = "sync") {
                                onMerge(branch)
                            },
                            AslOverflowMenuEntry.Item(publish, icon = "upload") {
                                interactionListener.publish(branch.name)
                            },
                            AslOverflowMenuEntry.Item(rename, icon = "pencil") {
                                onRename(branch)
                            },
                            AslOverflowMenuEntry.Divider,
                            AslOverflowMenuEntry.Item(delete, icon = "trash-2", destructive = true) {
                                onDelete(branch)
                            },
                        ),
                    )
                }
            }
            if (remote.isNotEmpty()) {
                item { SectionLabel(stringResource(R.string.git_refs_remote)) }
                items(remote, key = { "remote:${it.name}" }) { branch ->
                    BranchActionRow(
                        branch = branch,
                        entries = listOf(
                            AslOverflowMenuEntry.Item(checkout, icon = "git-branch") {
                                interactionListener.checkout(branch)
                            },
                        ),
                    )
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
) {
    AslListItem(
        title = branch.name.middleEllipsis(),
        subtitle = if (branch.current) stringResource(R.string.git_refs_checked_out) else null,
        icon = if (branch.current) "check" else "git-branch",
        iconColor = if (branch.current) AslTheme.colors.success else null,
        trailing = {
            AslOverflowMenu(items = entries)
        },
    )
}

@Composable
private fun TagList(tags: List<GitTag>, interactionListener: GitRefsInteractionListener, onDelete: (GitTag) -> Unit) {
    if (tags.isEmpty()) return AslEmptyState(stringResource(R.string.git_refs_no_tags), modifier = Modifier.fillMaxSize(), icon = "tag")
    LazyColumn(Modifier.fillMaxSize()) {
        items(tags, key = { it.name }) { tag ->
            RefRow(tag.name, if (tag.annotated) tag.message ?: stringResource(R.string.git_refs_annotated) else stringResource(R.string.git_refs_lightweight)) {
                AslButton(stringResource(R.string.git_action_push), { interactionListener.pushTag(tag.name) }, variant = AslButtonVariant.Tertiary)
                AslButton(stringResource(CommonR.string.action_delete), { onDelete(tag) }, variant = AslButtonVariant.Tertiary)
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
    if (stashes.isEmpty()) return AslEmptyState(stringResource(R.string.git_refs_no_stashes), modifier = Modifier.fillMaxSize(), icon = "package")
    LazyColumn(Modifier.fillMaxSize()) {
        items(stashes, key = { it.id }) { stash ->
            RefRow("stash@{${stash.index}}", stash.message) {
                AslButton(stringResource(R.string.git_refs_apply), { interactionListener.applyStash(stash.index) }, variant = AslButtonVariant.Tertiary)
                AslButton(stringResource(R.string.git_refs_pop), { onPop(stash) }, variant = AslButtonVariant.Tertiary)
                AslButton(stringResource(R.string.git_refs_drop), { onDrop(stash) }, variant = AslButtonVariant.Tertiary)
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
        confirmLabel = stringResource(CommonR.string.action_delete),
        cancelLabel = stringResource(CommonR.string.action_cancel),
        destructive = true,
        onDismiss = dismiss,
        onConfirm = confirm,
    )
}
