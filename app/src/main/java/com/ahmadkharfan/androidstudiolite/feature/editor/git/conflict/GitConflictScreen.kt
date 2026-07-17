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

@Composable
fun GitConflictRoute(
    projectId: String,
    onBack: () -> Unit,
    onOpenEditor: (String) -> Unit,
    viewModel: GitConflictViewModel = koinViewModel { parametersOf(projectId) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Scaffold(topBar = { AslTopAppBar("Resolve conflicts", onBack = onBack, applyStatusBarInset = true) }) { padding ->
        when {
            state.loading -> AslLinearProgress(
                label = "Loading conflicts",
                modifier = Modifier.padding(padding).padding(16.dp),
            )
            state.error != null -> AslEmptyState(
                title = "Couldn't load conflicts",
                subtitle = state.error,
                icon = "triangle-alert",
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            state.entries.isEmpty() -> AslEmptyState(
                title = "No unresolved files",
                subtitle = "Continue or complete the Git operation.",
                icon = "check",
                modifier = Modifier.padding(padding).fillMaxSize(),
            )
            else -> LazyColumn(Modifier.padding(padding).fillMaxSize()) {
                items(state.entries, key = { it.path }) { entry ->
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(entry.path.middleEllipsis(), style = MaterialTheme.typography.titleSmall, fontFamily = FontFamily.Monospace)
                        Text(entry.worktree.orEmpty().lineSequence().take(8).joinToString("\n"), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AslButton("Accept ours", { viewModel.acceptOurs(entry.path) }, variant = AslButtonVariant.Secondary)
                            AslButton("Accept theirs", { viewModel.acceptTheirs(entry.path) }, variant = AslButtonVariant.Secondary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AslButton("Open in editor", { onOpenEditor(File(state.rootPath, entry.path).absolutePath) }, variant = AslButtonVariant.Tertiary)
                            AslButton("Mark resolved", { viewModel.markResolved(entry.path) }, variant = AslButtonVariant.Primary)
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
    state.markerOverridePath?.let { path ->
        AslDialog(
            title = "Conflict markers remain",
            body = "$path still contains conflict-marker lines. Mark it resolved anyway?",
            variant = AslDialogVariant.Confirm,
            confirmLabel = "Mark resolved",
            cancelLabel = "Keep editing",
            destructive = true,
            onDismiss = viewModel::dismissMarkerWarning,
            onConfirm = { viewModel.markResolved(path, allowMarkers = true) },
        )
    }
}
