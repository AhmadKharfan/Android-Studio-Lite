package com.example.androidstudiolite.feature.editor.git.screen

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.androidstudiolite.core.designsystem.component.buttons.AslButton
import com.example.androidstudiolite.core.designsystem.component.buttons.AslIconButton
import com.example.androidstudiolite.core.designsystem.component.content.AslDiffLine
import com.example.androidstudiolite.core.designsystem.component.content.AslEmptyState
import com.example.androidstudiolite.core.designsystem.component.content.AslListItem
import com.example.androidstudiolite.core.designsystem.component.inputs.AslChip
import com.example.androidstudiolite.core.designsystem.component.inputs.AslChipKind
import com.example.androidstudiolite.core.designsystem.component.inputs.AslChipStatus
import com.example.androidstudiolite.core.designsystem.component.inputs.AslTextField
import com.example.androidstudiolite.core.designsystem.component.navigation.AslToolWindowPanel
import com.example.androidstudiolite.core.designsystem.theme.AslTheme
import com.example.androidstudiolite.domain.model.GitFileStatus
import com.example.androidstudiolite.feature.editor.git.interaction.GitPanelInteraction
import com.example.androidstudiolite.feature.editor.git.uiState.GitChangeUiModel
import com.example.androidstudiolite.feature.editor.git.uiState.GitPanelUiState
import com.example.androidstudiolite.feature.editor.git.viewModel.GitPanelViewModel

@Composable
fun GitPanelRoute(onClose: () -> Unit, viewModel: GitPanelViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GitPanelScreen(uiState = uiState, onInteraction = viewModel::onInteraction, onClose = onClose)
}

@Composable
private fun GitPanelScreen(
    uiState: GitPanelUiState,
    onInteraction: (GitPanelInteraction) -> Unit,
    onClose: () -> Unit,
) {
    AslToolWindowPanel(
        title = "Git · ${uiState.branch}",
        width = 280.dp,
        onClose = onClose,
        scrollable = false,
    ) {
        if (uiState.selectedPath != null) {
            DiffView(uiState = uiState, onInteraction = onInteraction)
        } else {
            ChangesView(uiState = uiState, onInteraction = onInteraction)
        }
    }
}

@Composable
private fun ChangesView(uiState: GitPanelUiState, onInteraction: (GitPanelInteraction) -> Unit) {
    val colors = AslTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        if (uiState.changes.isEmpty()) {
            AslEmptyState(
                icon = "git-commit",
                title = "No local changes",
                subtitle = "Edit files to see them appear here for commit.",
                modifier = Modifier.weight(1f).fillMaxSize(),
            )
        } else {
            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Text(
                    text = "CHANGES (${uiState.changes.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textTertiary,
                    modifier = Modifier.padding(start = 14.dp, top = 10.dp, bottom = 4.dp),
                )
                uiState.changes.forEach { change ->
                    AslListItem(
                        title = change.path,
                        icon = "file-code",
                        trailing = { GitStatusBadge(change.status) },
                        onClick = { onInteraction(GitPanelInteraction.SelectChange(change.path)) },
                    )
                }
            }
        }
        HorizontalDivider(color = colors.borderSubtle, thickness = 1.dp)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            AslTextField(
                value = uiState.commitMessage,
                onValueChange = { onInteraction(GitPanelInteraction.CommitMessageChanged(it)) },
                placeholder = "Commit message",
            )
            AslButton(
                label = "Commit",
                onClick = { onInteraction(GitPanelInteraction.Commit) },
                icon = "git-commit",
                fullWidth = true,
                loading = uiState.committing,
                disabled = uiState.commitMessage.isBlank() || uiState.changes.isEmpty(),
            )
        }
    }
}

@Composable
private fun DiffView(uiState: GitPanelUiState, onInteraction: (GitPanelInteraction) -> Unit) {
    val colors = AslTheme.colors
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AslIconButton(icon = "arrow-left", contentDescription = "Back", onClick = { onInteraction(GitPanelInteraction.CloseDiff) })
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
