package com.ahmadkharfan.androidstudiolite.feature.editor.git.conflict

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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslEmptyState
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialog
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialogVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import java.io.File
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.ahmadkharfan.androidstudiolite.feature.git.middleEllipsis
import com.ahmadkharfan.androidstudiolite.feature.git.R

@Composable
fun GitConflictRoute(
    projectId: String,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    viewModel: GitConflictViewModel = koinViewModel { parametersOf(projectId) },
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    GitConflictScreen(
        uiState = uiState,
        interactionListener = viewModel,
        onBack = onBack,
        onOpenEditor = onOpenEditor,
    )
}

@Composable
private fun GitConflictScreen(
    uiState: GitConflictUiState,
    interactionListener: GitConflictInteractionListener,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
) {
    Scaffold(topBar = { AslTopAppBar(stringResource(R.string.git_conflict_title), onBack = onBack, applyStatusBarInset = true) }) { padding ->
        when {
            uiState.loading -> AslLinearProgress(
                label = stringResource(R.string.git_conflict_loading),
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            uiState.error != null -> AslEmptyState(
                title = stringResource(R.string.git_conflict_load_error),
                subtitle = uiState.error,
                icon = "triangle-alert",
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            uiState.entries.isEmpty() -> AslEmptyState(
                title = stringResource(R.string.git_conflict_none),
                subtitle = stringResource(R.string.git_conflict_none_hint),
                icon = "check",
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(uiState.entries, key = { it.path }) { entry ->
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(entry.path.middleEllipsis(), style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
                        Text(entry.worktree.orEmpty().lineSequence().take(8).joinToString("\n"), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AslButton(stringResource(R.string.git_conflict_accept_ours), { interactionListener.acceptOurs(entry.path) }, variant = AslButtonVariant.Secondary)
                            AslButton(stringResource(R.string.git_conflict_accept_theirs), { interactionListener.acceptTheirs(entry.path) }, variant = AslButtonVariant.Secondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AslButton(stringResource(R.string.git_conflict_open_editor), { onOpenEditor(File(uiState.rootPath, entry.path).absolutePath) }, variant = AslButtonVariant.Tertiary)
                            AslButton(stringResource(R.string.git_conflict_mark_resolved), { interactionListener.markResolved(entry.path) }, variant = AslButtonVariant.Primary)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    uiState.markerOverridePath?.let { path ->
        AslDialog(
            title = stringResource(R.string.git_conflict_markers_title),
            body = stringResource(R.string.git_conflict_markers_body, path),
            variant = AslDialogVariant.Confirm,
            confirmLabel = stringResource(R.string.git_conflict_mark_resolved),
            cancelLabel = stringResource(R.string.git_conflict_keep_editing),
            destructive = true,
            onDismiss = interactionListener::dismissMarkerWarning,
            onConfirm = { interactionListener.markResolved(path, allowMarkers = true) },
        )
    }
}
