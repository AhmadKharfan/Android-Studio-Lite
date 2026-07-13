package com.ahmadkharfan.androidstudiolite.feature.editor.git
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.ahmadkharfan.androidstudiolite.designsystem.animation.AslSlideContent
import com.ahmadkharfan.androidstudiolite.designsystem.animation.AslStateCrossfade
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslDiffLine
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslEmptyState
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslListItem
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChip
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChipKind
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChipStatus
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslToolWindowPanel
import com.ahmadkharfan.androidstudiolite.designsystem.theme.AslTheme
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileStatus
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitPanelInteractionListener
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitChangeUiModel
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitPanelUiState
import com.ahmadkharfan.androidstudiolite.feature.editor.git.GitPanelViewModel

@Composable
fun GitPanelRoute(
    projectId: String,
    onClose: () -> Unit,
    viewModel: GitPanelViewModel = koinViewModel { parametersOf(projectId) },
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    GitPanelScreen(uiState = uiState, interactionListener = viewModel, onClose = onClose)
}

@Composable
private fun GitPanelScreen(
    uiState: GitPanelUiState,
    interactionListener: GitPanelInteractionListener,
    onClose: () -> Unit,
) {
    AslToolWindowPanel(
        title = "Git · ${uiState.branch}",
        width = 280.dp,
        onClose = onClose,
        scrollable = false,
    ) {
        if (!uiState.isRepository) {
            AslEmptyState(
                icon = "git-branch",
                title = "Not a git repository",
                subtitle = "Clone a repository or initialise git for this project to see changes here.",
                modifier = Modifier.fillMaxSize(),
            )
            return@AslToolWindowPanel
        }
        // Slide between the changes list (master) and a file's diff (detail); each pane keeps its own
        // state through the animation so going back doesn't flash an empty, already-cleared diff.
        AslSlideContent(
            targetState = uiState,
            contentKey = { it.selectedPath != null },
            isForward = { initial, target -> initial.selectedPath == null && target.selectedPath != null },
            label = "gitMasterDetail",
        ) { state ->
            if (state.selectedPath != null) {
                DiffView(uiState = state, interactionListener = interactionListener)
            } else {
                ChangesView(uiState = state, interactionListener = interactionListener)
            }
        }
    }
}

@Composable
private fun ChangesView(uiState: GitPanelUiState, interactionListener: GitPanelInteractionListener) {
    val colors = AslTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        GitSyncBar(uiState = uiState, interactionListener = interactionListener)
        HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
        GitChangedFileList(uiState = uiState, interactionListener = interactionListener, modifier = Modifier.weight(1f).fillMaxSize())
        HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
        GitCommitBox(uiState = uiState, interactionListener = interactionListener)
    }
}

@Composable
private fun GitSyncBar(uiState: GitPanelUiState, interactionListener: GitPanelInteractionListener) {
    val colors = AslTheme.colors
    LaunchedEffect(uiState.statusMessage) {
        if (uiState.statusMessage != null) {
            delay(4000)
            interactionListener.onStatusMessageShown()
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        val syncLabel = buildString {
            uiState.behind?.takeIf { it > 0 }?.let { append("↓$it ") }
            uiState.ahead?.takeIf { it > 0 }?.let { append("↑$it") }
        }.trim()
        Text(
            text = uiState.statusMessage ?: syncLabel,
            style = MaterialTheme.typography.labelSmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        AslIconButton(icon = "refresh-cw", contentDescription = "Refresh", onClick = { interactionListener.onRefresh() }, size = 32.dp, iconSize = 16.dp)
        AslIconButton(icon = "download", contentDescription = "Pull", onClick = { interactionListener.onPull() }, size = 32.dp, iconSize = 16.dp)
        AslIconButton(icon = "upload", contentDescription = "Push", onClick = { interactionListener.onPush() }, size = 32.dp, iconSize = 16.dp)
    }
}

@Composable
private fun GitChangedFileList(
    uiState: GitPanelUiState,
    interactionListener: GitPanelInteractionListener,
    modifier: Modifier = Modifier,
) {
    // Ease between the empty state and the populated change list (e.g. after a commit clears it).
    AslStateCrossfade(
        targetState = uiState.changes.isEmpty(),
        modifier = modifier,
        label = "gitChanges",
    ) { isEmpty ->
        if (isEmpty) {
            AslEmptyState(
                icon = "git-commit",
                title = "No local changes",
                subtitle = "Edit files to see them appear here for commit.",
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                GitChangeSection(
                    title = "STAGED",
                    changes = uiState.stagedChanges,
                    staged = true,
                    interactionListener = interactionListener,
                )
                GitChangeSection(
                    title = "CHANGES",
                    changes = uiState.unstagedChanges,
                    staged = false,
                    interactionListener = interactionListener,
                )
            }
        }
    }
}

@Composable
private fun GitChangeSection(
    title: String,
    changes: List<GitChangeUiModel>,
    staged: Boolean,
    interactionListener: GitPanelInteractionListener,
) {
    if (changes.isEmpty()) return
    val colors = AslTheme.colors
    Text(
        text = "$title (${changes.size})",
        style = MaterialTheme.typography.labelSmall,
        color = colors.textTertiary,
        modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 4.dp),
    )
    changes.forEach { change ->
        AslListItem(
            title = change.path,
            icon = "file-code",
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    GitStatusBadge(change.status)
                    if (staged) {
                        AslIconButton(
                            icon = "minus",
                            contentDescription = "Unstage ${change.path}",
                            onClick = { interactionListener.onUnstage(change.path) },
                            size = 28.dp,
                            iconSize = 14.dp,
                        )
                    } else {
                        AslIconButton(
                            icon = "plus",
                            contentDescription = "Stage ${change.path}",
                            onClick = { interactionListener.onStage(change.path) },
                            size = 28.dp,
                            iconSize = 14.dp,
                        )
                    }
                }
            },
            onClick = { interactionListener.onSelectChange(change.path) },
        )
    }
}

@Composable
private fun GitCommitBox(
    uiState: GitPanelUiState,
    interactionListener: GitPanelInteractionListener,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AslTextField(
            value = uiState.commitMessage,
            onValueChange = { interactionListener.onCommitMessageChanged(it) },
            placeholder = "Commit message",
        )
        AslButton(
            label = "Commit",
            onClick = { interactionListener.onCommit() },
            icon = "git-commit",
            fullWidth = true,
            loading = uiState.committing,
            disabled = !uiState.canCommit,
        )
    }
}

@Composable
private fun DiffView(uiState: GitPanelUiState, interactionListener: GitPanelInteractionListener) {
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
        }
        HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            uiState.diffLines.forEach { line ->
                AslDiffLine(kind = line.kind, text = line.text, oldNo = line.oldNo, newNo = line.newNo)
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
    }
    AslChip(label = label, kind = AslChipKind.Status, status = chipStatus)
}
