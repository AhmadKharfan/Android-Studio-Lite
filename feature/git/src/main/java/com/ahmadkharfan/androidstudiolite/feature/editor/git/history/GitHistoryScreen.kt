package com.ahmadkharfan.androidstudiolite.feature.editor.git.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslEmptyState
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChip
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChipKind
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslChipStatus
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslOverflowMenu
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslOverflowMenuEntry
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialog
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslDialogVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslTextField
import com.ahmadkharfan.androidstudiolite.domain.model.GitResetMode
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommitDetails
import com.ahmadkharfan.androidstudiolite.domain.model.GitCommitSummary
import kotlinx.coroutines.flow.distinctUntilChanged
import com.ahmadkharfan.androidstudiolite.feature.git.middleEllipsis
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.ahmadkharfan.androidstudiolite.core.common.R as CommonR
import com.ahmadkharfan.androidstudiolite.feature.git.R

@Composable
fun GitHistoryRoute(
    projectId: String,
    path: String?,
    onBack: () -> Unit,
    onOpenDiff: (path: String, commitId: String) -> Unit,


    viewModel: GitHistoryViewModel = koinViewModel { parametersOf(projectId, path.orEmpty()) },
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    GitHistoryScreen(
        uiState = uiState,
        interactionListener = viewModel,
        onBack = onBack,
        onOpenDiff = onOpenDiff,
    )
}

@Composable
private fun GitHistoryScreen(
    uiState: GitHistoryUiState,
    interactionListener: GitHistoryInteractionListener,
    onBack: () -> Unit,
    onOpenDiff: (String, String) -> Unit,
) {
    var resetCommit by remember { mutableStateOf<String?>(null) }
    var resetMode by remember { mutableStateOf(GitResetMode.MIXED) }
    var resetConfirmation by remember { mutableStateOf("") }
    Scaffold(
        topBar = {
            AslTopAppBar(
                title = stringResource(if (uiState.path == null) R.string.git_history_title else R.string.git_history_file_title),
                subtitle = uiState.path?.middleEllipsis(),
                onBack = if (uiState.selected == null) onBack else interactionListener::clearSelection,
                applyStatusBarInset = true,
                actions = {
                    if (uiState.selected == null && uiState.path == null) {
                        AslButton(
                            label = stringResource(if (uiState.graphEnabled) R.string.git_history_graph_on else R.string.git_history_graph_off),
                            onClick = interactionListener::toggleGraph,
                            variant = AslButtonVariant.Tertiary,
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            when {
                uiState.loading -> AslLinearProgress(label = stringResource(R.string.git_history_loading), modifier = Modifier.padding(16.dp))
                uiState.error != null && uiState.commits.isEmpty() -> AslEmptyState(
                    title = stringResource(R.string.git_history_error),
                    subtitle = uiState.error,
                    icon = "triangle-alert",
                    modifier = Modifier.fillMaxSize(),
                )
                uiState.selected != null -> CommitDetails(uiState.selected, onOpenDiff)
                uiState.commits.isEmpty() -> AslEmptyState(
                    title = stringResource(R.string.git_history_no_commits),
                    subtitle = stringResource(R.string.git_history_no_commits_hint),
                    icon = "git-commit",
                    modifier = Modifier.fillMaxSize(),
                )
                else -> HistoryList(uiState, interactionListener::loadNext, interactionListener::select, interactionListener::deepen, { commit ->
                    resetCommit = commit
                    resetMode = GitResetMode.MIXED
                    resetConfirmation = ""
                })
            }
        }
    }
    resetCommit?.let { commit ->
        AslDialog(
            title = stringResource(R.string.git_history_reset_title, commit.take(7)),
            body = when (resetMode) {
                GitResetMode.SOFT -> stringResource(R.string.git_history_reset_soft)
                GitResetMode.MIXED -> stringResource(R.string.git_history_reset_mixed)
                GitResetMode.HARD -> stringResource(R.string.git_history_reset_hard)
            },
            variant = AslDialogVariant.Confirm,
            confirmLabel = stringResource(R.string.git_history_reset),
            cancelLabel = stringResource(CommonR.string.action_cancel),
            destructive = resetMode == GitResetMode.HARD,
            onDismiss = { resetCommit = null },
            onConfirm = {
                if (resetMode != GitResetMode.HARD || resetConfirmation == "RESET") {
                    interactionListener.reset(commit, resetMode)
                    resetCommit = null
                }
            },
            inputContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        GitResetMode.entries.forEach { mode ->
                            AslChip(
                                label = stringResource(
                                    when (mode) {
                                        GitResetMode.SOFT -> R.string.git_history_reset_mode_soft
                                        GitResetMode.MIXED -> R.string.git_history_reset_mode_mixed
                                        GitResetMode.HARD -> R.string.git_history_reset_mode_hard
                                    },
                                ),
                                kind = AslChipKind.Filter,
                                selected = resetMode == mode,
                                onClick = { resetMode = mode },
                            )
                        }
                    }
                    if (resetMode == GitResetMode.HARD) {
                        AslTextField(resetConfirmation, { resetConfirmation = it }, placeholder = stringResource(R.string.git_history_type_reset))
                    }
                }
            },
        )
    }
}

@Composable
private fun HistoryList(
    state: GitHistoryUiState,
    onLoadNext: () -> Unit,
    onSelect: (String) -> Unit,
    onDeepen: () -> Unit,
    onReset: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(listState, state.nextCursor) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .distinctUntilChanged()
            .collect { index -> if (state.nextCursor != null && index >= state.commits.lastIndex - 4) onLoadNext() }
    }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(state.commits, key = { it.id }) { commit ->
            HistoryRow(commit, state.graphRows[commit.id].takeIf { state.graphEnabled }, { onSelect(commit.id) }, { onReset(commit.id) })
        }
        if (state.loadingMore) item { AslLinearProgress(label = stringResource(R.string.git_history_loading_more), modifier = Modifier.padding(16.dp)) }
        if (state.shallow && state.nextCursor == null) {
            item {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.git_history_shallow), style = MaterialTheme.typography.bodyMedium)
                    AslButton(stringResource(R.string.git_history_deepen), onDeepen, variant = AslButtonVariant.Secondary)
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(commit: GitCommitSummary, graph: GitGraphRow?, onClick: () -> Unit, onReset: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        if (graph != null) {
            GitGraphGutter(graph)
        }
        Column(
            Modifier
                .weight(1f)
                .padding(
                    start = if (graph != null) 8.dp else 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                commit.message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(commit.shortId, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
            AslOverflowMenu(
                items = listOf(AslOverflowMenuEntry.Item(stringResource(R.string.git_history_reset_here), icon = "rotate-ccw", destructive = true)),
                onSelect = { _, _ -> onReset() },
            )
        }
        Text(
            "${commit.authorName} · ${relativeTime(commit.authorTimeMillis)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (commit.refs.isNotEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                commit.refs.take(4).forEach { ref ->
                    AslChip(
                        label = ref.name,
                        kind = AslChipKind.Status,
                        status = if (ref.kind.name == "TAG") AslChipStatus.Warning else AslChipStatus.Info,
                    )
                }
            }
        }
        if (commit.isShallowBoundary) Text(stringResource(R.string.git_history_shallow_boundary), style = MaterialTheme.typography.labelSmall)
        commit.path?.let { Text(it.middleEllipsis(), style = MaterialTheme.typography.labelSmall, fontFamily = FontFamily.Monospace) }
        }
    }
    HorizontalDivider()
}

private val GitGraphGutterWidth = 44.dp
private val GitGraphLaneWidth = 10.dp
private val GitGraphRowHeight = 72.dp

@Composable
private fun GitGraphGutter(row: GitGraphRow) {
    val palette = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.error,
        Color(0xFF43A047),
        Color(0xFFFF8F00),
    )
    Box(
        modifier = Modifier
            .width(GitGraphGutterWidth)
            .height(GitGraphRowHeight)
            .padding(start = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val laneStep = GitGraphLaneWidth.toPx()
            val laneSpan = laneStep * row.laneCount
            val laneOriginX = ((size.width - laneSpan) / 2f).coerceAtLeast(0f)
            fun x(lane: Int): Float {
                val laneIndex = lane.coerceIn(0, row.laneCount - 1)
                return laneOriginX + laneStep * laneIndex + laneStep / 2f
            }
            val dotY = size.height / 2f
            val stroke = 2.dp.toPx()
            if (row.hasIncoming) {
                drawLine(
                    color = palette[row.lane % palette.size],
                    start = Offset(x(row.lane), 0f),
                    end = Offset(x(row.lane), dotY),
                    strokeWidth = stroke,
                )
            }
            row.edges.forEach { edge ->
                drawLine(
                    color = palette[edge.fromLane % palette.size],
                    start = Offset(x(edge.fromLane), if (edge.fromLane == row.lane) dotY else 0f),
                    end = Offset(x(edge.toLane), size.height),
                    strokeWidth = stroke,
                )
            }
            drawCircle(
                color = palette[row.lane % palette.size],
                radius = if (row.collapsed) 5.dp.toPx() else 4.dp.toPx(),
                center = Offset(x(row.lane), dotY),
            )
        }
    }
}

@Composable
private fun CommitDetails(details: GitCommitDetails, onOpenDiff: (String, String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            val initialCommit = stringResource(R.string.git_history_initial_commit)
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(details.fullMessage, style = MaterialTheme.typography.titleMedium)
                Text("${details.author.name} <${details.author.email}>", style = MaterialTheme.typography.bodyMedium)
                Text(details.id, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodySmall)
                Text(
                    stringResource(
                        R.string.git_history_parents,
                        details.parents.joinToString().ifEmpty { initialCommit },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            HorizontalDivider()
        }
        items(details.changedFiles, key = { "${it.oldPath}:${it.path}" }) { change ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpenDiff(change.path, details.id) }.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(change.type.name.first().toString(), fontFamily = FontFamily.Monospace)
                Text(
                    (change.oldPath?.let { "$it → ${change.path}" } ?: change.path).middleEllipsis(),
                    modifier = Modifier.weight(1f),
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider()
        }
    }
}

@Composable
private fun relativeTime(timeMillis: Long): String {
    val seconds = ((System.currentTimeMillis() - timeMillis).coerceAtLeast(0L) / 1_000L)
    return when {
        seconds < 60 -> stringResource(R.string.git_time_just_now)
        seconds < 3_600 -> stringResource(R.string.git_time_minutes_ago, seconds / 60)
        seconds < 86_400 -> stringResource(R.string.git_time_hours_ago, seconds / 3_600)
        seconds < 2_592_000 -> stringResource(R.string.git_time_days_ago, seconds / 86_400)
        else -> stringResource(R.string.git_time_months_ago, seconds / 2_592_000)
    }
}
