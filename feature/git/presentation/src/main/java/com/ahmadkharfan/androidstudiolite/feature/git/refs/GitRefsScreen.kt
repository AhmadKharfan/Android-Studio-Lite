package com.ahmadkharfan.androidstudiolite.feature.git.refs

import kotlin.time.Duration.Companion.seconds
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslHorizontalDivider
import com.ahmadkharfan.androidstudiolite.designsystem.component.ide.AslScaffold
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslText
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
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTextStyles
import com.ahmadkharfan.androidstudiolite.core.gitauth.GitHubAuthDialog
import com.ahmadkharfan.androidstudiolite.domain.model.GitBranch
import com.ahmadkharfan.androidstudiolite.domain.model.GitStash
import com.ahmadkharfan.androidstudiolite.domain.model.GitTag
import com.ahmadkharfan.androidstudiolite.domain.model.PullMode
import com.ahmadkharfan.androidstudiolite.feature.git.aheadLabel
import com.ahmadkharfan.androidstudiolite.feature.git.behindLabel
import com.ahmadkharfan.androidstudiolite.feature.git.stashLabel
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
    val colors = AslTheme.colors
    val dialogState = remember { GitRefsDialogState() }
    val title = when (uiState.mode) {
        GitRefsMode.BRANCHES -> stringResource(R.string.git_refs_branches)
        GitRefsMode.TAGS -> stringResource(R.string.git_refs_tags)
        GitRefsMode.STASHES -> stringResource(R.string.git_refs_stashes)
    }
    AslScaffold(
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
                        onClick = { dialogState.name = ""; dialogState.message = ""; dialogState.createOpen = true },
                        variant = AslButtonVariant.Tertiary,
                        disabled = uiState.loading,
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (uiState.error != null) {
                AslText(
                    uiState.error,
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    color = colors.error,
                    style = AslTextStyles.bodySmall,
                )
            }
            if (uiState.loading) AslLinearProgress(label = stringResource(R.string.git_refs_updating, title), modifier = Modifier.padding(12.dp))
            when (uiState.mode) {
                GitRefsMode.BRANCHES -> BranchList(
                    state = uiState,
                    interactionListener = interactionListener,
                    onRename = { branch -> dialogState.name = branch.name; dialogState.rename = branch },
                    onDelete = { dialogState.deleteBranch = it },
                    onMerge = { dialogState.mergeBranch = it },
                )
                GitRefsMode.TAGS -> TagList(uiState.tags, interactionListener) { dialogState.deleteTag = it }
                GitRefsMode.STASHES -> StashList(
                    uiState.stashes,
                    interactionListener,
                    onPop = { dialogState.popStash = it },
                ) { dialogState.dropStash = it }
            }
        }
    }

    GitRefsBranchDialogs(dialogState, uiState, interactionListener)
    GitHubAuthDialog(uiState.authPrompt, interactionListener)
    GitRefsRefDialogs(dialogState, interactionListener)
}

@Composable
private fun GitRefsCreateDialog(
    dialogState: GitRefsDialogState,
    uiState: GitRefsUiState,
    interactionListener: GitRefsInteractionListener,
) {
    if (dialogState.createOpen) {
        AslDialog(
            title = when (uiState.mode) {
                GitRefsMode.BRANCHES -> stringResource(R.string.git_refs_create_branch)
                GitRefsMode.TAGS -> stringResource(R.string.git_refs_create_tag)
                GitRefsMode.STASHES -> stringResource(R.string.git_refs_stash_changes)
            },
            variant = AslDialogVariant.Input,
            confirmLabel = stringResource(CommonR.string.action_create),
            cancelLabel = stringResource(CommonR.string.action_cancel),
            onDismiss = { dialogState.createOpen = false },
            onConfirm = {
                when (uiState.mode) {
                    GitRefsMode.BRANCHES -> interactionListener.createBranch(dialogState.name)
                    GitRefsMode.TAGS -> interactionListener.createTag(dialogState.name, dialogState.message)
                    GitRefsMode.STASHES -> interactionListener.createStash(dialogState.message, dialogState.includeUntracked)
                }
                dialogState.createOpen = false
            },
            inputContent = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (uiState.mode != GitRefsMode.STASHES) AslTextField(dialogState.name, { dialogState.name = it }, label = stringResource(R.string.git_refs_name))
                    if (uiState.mode != GitRefsMode.BRANCHES) AslTextField(dialogState.message, { dialogState.message = it }, label = stringResource(R.string.git_refs_message))
                    if (uiState.mode == GitRefsMode.STASHES) {
                        AslCheckbox(dialogState.includeUntracked, { dialogState.includeUntracked = it }, label = stringResource(R.string.git_refs_include_untracked))
                    }
                }
            },
        )
    }
}

@Composable
private fun GitRefsBranchDialogs(
    dialogState: GitRefsDialogState,
    uiState: GitRefsUiState,
    interactionListener: GitRefsInteractionListener,
) {
    GitRefsCreateDialog(dialogState, uiState, interactionListener)
    dialogState.rename?.let { branch ->
        AslDialog(
            title = stringResource(R.string.git_refs_rename_title, branch.name),
            variant = AslDialogVariant.Input,
            confirmLabel = stringResource(CommonR.string.action_rename),
            cancelLabel = stringResource(CommonR.string.action_cancel),
            onDismiss = { dialogState.rename = null },
            onConfirm = { interactionListener.renameBranch(branch.name, dialogState.name); dialogState.rename = null },
            inputContent = { AslTextField(dialogState.name, { dialogState.name = it }, label = stringResource(R.string.git_refs_new_name)) },
        )
    }
    dialogState.deleteBranch?.let { branch ->
        ConfirmDelete(
            title = stringResource(R.string.git_refs_delete_branch_title, branch.name),
            body = stringResource(R.string.git_refs_delete_branch_body),
            confirm = { interactionListener.deleteBranch(branch.name); dialogState.deleteBranch = null },
            dismiss = { dialogState.deleteBranch = null },
        )
    }
    dialogState.mergeBranch?.let { branch ->
        AslDialog(
            title = stringResource(R.string.git_refs_merge_title, branch.name),
            body = stringResource(R.string.git_refs_merge_body, branch.name),
            variant = AslDialogVariant.Confirm,
            confirmLabel = stringResource(R.string.git_refs_merge),
            cancelLabel = stringResource(CommonR.string.action_cancel),
            onDismiss = { dialogState.mergeBranch = null },
            onConfirm = { interactionListener.merge(branch.name); dialogState.mergeBranch = null },
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
}

@Composable
private fun GitRefsRefDialogs(
    dialogState: GitRefsDialogState,
    interactionListener: GitRefsInteractionListener,
) {
    dialogState.deleteTag?.let { tag ->
        ConfirmDelete(
            title = stringResource(R.string.git_refs_delete_tag_title, tag.name),
            body = stringResource(R.string.git_refs_delete_tag_body),
            confirm = { interactionListener.deleteTag(tag.name); dialogState.deleteTag = null },
            dismiss = { dialogState.deleteTag = null },
        )
    }
    dialogState.popStash?.let { stash ->
        AslDialog(
            title = stringResource(R.string.git_refs_pop_stash_title, stash.index),
            body = stringResource(R.string.git_refs_pop_stash_body),
            variant = AslDialogVariant.Confirm,
            confirmLabel = stringResource(R.string.git_refs_pop),
            cancelLabel = stringResource(CommonR.string.action_cancel),
            onDismiss = { dialogState.popStash = null },
            onConfirm = { interactionListener.popStash(stash.index); dialogState.popStash = null },
        )
    }
    dialogState.dropStash?.let { stash ->
        ConfirmDelete(
            title = stringResource(R.string.git_refs_drop_stash_title, stash.index),
            body = stringResource(R.string.git_refs_drop_stash_body),
            confirm = { interactionListener.dropStash(stash.index); dialogState.dropStash = null },
            dismiss = { dialogState.dropStash = null },
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
                state.behind?.takeIf { it > 0 }?.let { AslChip(label = behindLabel(it), kind = AslChipKind.Status, status = AslChipStatus.Info) }
                state.ahead?.takeIf { it > 0 }?.let { AslChip(label = aheadLabel(it), kind = AslChipKind.Status, status = AslChipStatus.Success) }
                AslText(
                    text = state.syncMessage.orEmpty(),
                    style = AslTextStyles.labelSmall,
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
        AslHorizontalDivider(color = colors.borderSubtle)

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
    AslText(
        text = text.uppercase(),
        style = AslTextStyles.labelSmall,
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
            RefRow(stashLabel(stash.index), stash.message) {
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
        AslText(name, fontFamily = FontFamily.Monospace, style = AslTextStyles.titleSmall)
        detail?.let { AslText(it, maxLines = 1, overflow = TextOverflow.Ellipsis, style = AslTextStyles.bodySmall) }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { actions() }
    }
    AslHorizontalDivider()
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
