package com.ahmadkharfan.androidstudiolite.feature.editor.git.diff

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInParent
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslButtonVariant
import com.ahmadkharfan.androidstudiolite.designsystem.component.buttons.AslIconButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslDiffKind
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslDiffLine
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSegmentedButton
import com.ahmadkharfan.androidstudiolite.designsystem.component.inputs.AslSegmentedOption
import com.ahmadkharfan.androidstudiolite.designsystem.component.content.AslEmptyState
import com.ahmadkharfan.androidstudiolite.designsystem.component.feedback.AslLinearProgress
import com.ahmadkharfan.androidstudiolite.designsystem.component.navigation.AslTopAppBar
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffHunk
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffLine
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffTarget
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf
import com.ahmadkharfan.androidstudiolite.feature.git.middleEllipsis

@Composable
fun GitDiffRoute(
    projectId: String,
    path: String,
    target: GitDiffTarget,
    commitId: String? = null,
    onBack: () -> Unit,
    viewModel: GitDiffViewModel = koinViewModel { parametersOf(projectId, path, target, commitId.orEmpty()) },
) {
    val uiState by viewModel.state.collectAsStateWithLifecycle()
    GitDiffScreen(uiState = uiState, interactionListener = viewModel, onBack = onBack)
}

@Composable
private fun GitDiffScreen(
    uiState: GitDiffUiState,
    interactionListener: GitDiffInteractionListener,
    onBack: () -> Unit,
) {

    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var landscape by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(landscape) {
        activity?.requestedOrientation =
            if (landscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    DisposableEffect(Unit) {
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    Scaffold(
        topBar = {
            AslTopAppBar(
                title = uiState.path.substringAfterLast('/'),
                subtitle = uiState.path.middleEllipsis(),
                onBack = onBack,
                applyStatusBarInset = true,
                actions = {
                    AslIconButton(
                        icon = if (landscape) "smartphone" else "monitor",
                        contentDescription = if (landscape) "Rotate to portrait" else "Rotate to landscape",
                        onClick = { landscape = !landscape },
                    )
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                AslSegmentedButton(
                    options = listOf(
                        AslSegmentedOption("Unified", "unified", icon = "align-left"),
                        AslSegmentedOption("Side-by-side", "split", icon = "layout"),
                    ),
                    value = if (uiState.sideBySide) "split" else "unified",
                    onValueChange = { interactionListener.setSideBySide(it == "split") },
                )
            }
            HorizontalDivider()
            when {
                uiState.loading -> AslLinearProgress(label = "Computing diff", modifier = Modifier.padding(16.dp))
                uiState.error != null -> AslEmptyState(
                    title = "Couldn't show diff",
                    icon = "triangle-alert",
                    subtitle = uiState.error,
                    modifier = Modifier.fillMaxSize(),
                )
                uiState.diff?.isBinary == true -> AslEmptyState(
                    title = "Binary file",
                    icon = "file",
                    subtitle = "Binary content cannot be displayed or partially staged.",
                    modifier = Modifier.fillMaxSize(),
                )
                uiState.diff?.tooLarge == true -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    AslEmptyState(
                        title = "Diff is large",
                        icon = "triangle-alert",
                        subtitle = "Files over 512 KiB or 20,000 lines are hidden to keep the editor responsive.",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    AslButton("Show anyway", interactionListener::showAnyway, modifier = Modifier.padding(16.dp))
                }
                uiState.diff?.hunks?.isEmpty() == true -> AslEmptyState(
                    title = "No differences",
                    icon = "check",
                    subtitle = "This side of the file matches its Git comparison target.",
                    modifier = Modifier.fillMaxSize(),
                )
                uiState.diff != null -> DiffContent(uiState, interactionListener::stage, interactionListener::unstage)
            }
        }
    }
}

@Composable
private fun ColumnScope.DiffContent(
    state: GitDiffUiState,
    onStage: (GitDiffHunk) -> Unit,
    onUnstage: (GitDiffHunk) -> Unit,
) {
    val diff = state.diff ?: return
    if (state.sideBySide) {
        SideBySideDiff(diff, state.target, onStage, onUnstage)
    } else {
        UnifiedDiff(diff, state.target, onStage, onUnstage)
    }
}

@Composable
private fun ColumnScope.UnifiedDiff(
    diff: com.ahmadkharfan.androidstudiolite.domain.model.GitFileDiff,
    target: GitDiffTarget,
    onStage: (GitDiffHunk) -> Unit,
    onUnstage: (GitDiffHunk) -> Unit,
) {
    val vScroll = rememberScrollState()
    val hScroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val headerOffsets = remember { mutableStateMapOf<Int, Int>() }
    if (diff.hunks.size > 1) {
        HunkNavBar(
            count = diff.hunks.size,
            onPrevious = { scope.launch { targetOffset(headerOffsets, vScroll.value, next = false)?.let { vScroll.animateScrollTo(it) } } },
            onNext = { scope.launch { targetOffset(headerOffsets, vScroll.value, next = true)?.let { vScroll.animateScrollTo(it) } } },
        )
    }
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(vScroll)) {
        diff.hunks.forEachIndexed { index, hunk ->
            HunkHeader(
                hunk = hunk,
                target = target,
                onStage = onStage,
                onUnstage = onUnstage,
                modifier = Modifier.onGloballyPositioned { headerOffsets[index] = it.boundsInParent().top.toInt() },
            )


            Column(Modifier.horizontalScroll(hScroll).width(IntrinsicSize.Max)) {
                hunk.lines.forEach { line ->
                    AslDiffLine(
                        kind = line.kind.toUiKind(),
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
private fun ColumnScope.SideBySideDiff(
    diff: com.ahmadkharfan.androidstudiolite.domain.model.GitFileDiff,
    target: GitDiffTarget,
    onStage: (GitDiffHunk) -> Unit,
    onUnstage: (GitDiffHunk) -> Unit,
) {
    val vScroll = rememberScrollState()
    val leftScroll = rememberScrollState()
    val rightScroll = rememberScrollState()
    Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(vScroll)) {
        diff.hunks.forEach { hunk ->
            HunkHeader(hunk, target, onStage, onUnstage)
            val paired = hunk.alignedRows()
            Row(Modifier.fillMaxWidth()) {
                DiffPaneColumn(paired.map { it.left }, isLeft = true, hScroll = leftScroll, modifier = Modifier.weight(1f))
                VerticalDivider()
                DiffPaneColumn(paired.map { it.right }, isLeft = false, hScroll = rightScroll, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun DiffPaneColumn(
    cells: List<GitDiffLine?>,
    isLeft: Boolean,
    hScroll: androidx.compose.foundation.ScrollState,
    modifier: Modifier = Modifier,
) {
    Box(modifier.horizontalScroll(hScroll)) {
        Column(Modifier.width(IntrinsicSize.Max)) {
            cells.forEach { cell -> DiffPaneCell(cell, isLeft) }
        }
    }
}

@Composable
private fun DiffPaneCell(line: GitDiffLine?, isLeft: Boolean) {
    val kind = line?.kind
    val bg = when (kind) {
        GitDiffKind.REMOVED -> MaterialTheme.colorScheme.error.copy(alpha = 0.10f)
        GitDiffKind.ADDED, GitDiffKind.MODIFIED -> MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
        else -> Color.Transparent
    }
    val number = if (isLeft) line?.oldNo else line?.newNo
    Row(
        Modifier.defaultMinSize(minHeight = 22.dp).background(bg),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            number?.toString().orEmpty(),
            modifier = Modifier.width(42.dp).padding(end = 6.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.End,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.labelSmall,
        )
        Text(
            line?.text.orEmpty(),
            modifier = Modifier.padding(end = 12.dp),
            fontFamily = FontFamily.Monospace,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            softWrap = false,
            maxLines = 1,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
private fun HunkNavBar(count: Int, onPrevious: () -> Unit, onNext: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        Text(
            "$count hunks",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        AslIconButton(icon = "chevron-up", contentDescription = "Previous hunk", onClick = onPrevious, size = 32.dp, iconSize = 16.dp)
        AslIconButton(icon = "chevron-down", contentDescription = "Next hunk", onClick = onNext, size = 32.dp, iconSize = 16.dp)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun HunkHeader(hunk: GitDiffHunk, target: GitDiffTarget, onStage: (GitDiffHunk) -> Unit, onUnstage: (GitDiffHunk) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp)) {
        Text("@@ -${hunk.oldStart},${hunk.oldCount} +${hunk.newStart},${hunk.newCount} @@", modifier = Modifier.weight(1f), fontFamily = FontFamily.Monospace)
        if (target != GitDiffTarget.COMMIT_TO_PARENT) {
            AslButton(
                if (target == GitDiffTarget.HEAD_TO_INDEX) "Unstage hunk" else "Stage hunk",
                { if (target == GitDiffTarget.HEAD_TO_INDEX) onUnstage(hunk) else onStage(hunk) },
                variant = AslButtonVariant.Tertiary,
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

internal sealed interface DiffRow {
    data class Header(val index: Int, val hunk: GitDiffHunk) : DiffRow
    data class Unified(val line: GitDiffLine) : DiffRow
    data class Paired(val left: GitDiffLine?, val right: GitDiffLine?) : DiffRow
}

internal fun GitDiffHunk.alignedRows(): List<DiffRow.Paired> {
    val rows = mutableListOf<DiffRow.Paired>()
    val pendingRemoved = mutableListOf<GitDiffLine>()
    val pendingAdded = mutableListOf<GitDiffLine>()
    fun flush() {
        repeat(maxOf(pendingRemoved.size, pendingAdded.size)) { index ->
            rows += DiffRow.Paired(pendingRemoved.getOrNull(index), pendingAdded.getOrNull(index))
        }
        pendingRemoved.clear(); pendingAdded.clear()
    }
    lines.forEach { line ->
        when (line.kind) {
            GitDiffKind.REMOVED -> pendingRemoved += line
            GitDiffKind.ADDED, GitDiffKind.MODIFIED -> pendingAdded += line
            GitDiffKind.CONTEXT -> { flush(); rows += DiffRow.Paired(line, line) }
        }
    }
    flush()
    return rows
}

private fun GitDiffKind.toUiKind() = when (this) {
    GitDiffKind.ADDED -> AslDiffKind.Added
    GitDiffKind.REMOVED -> AslDiffKind.Removed
    GitDiffKind.MODIFIED -> AslDiffKind.Modified
    GitDiffKind.CONTEXT -> AslDiffKind.Context
}

private fun targetOffset(offsets: Map<Int, Int>, current: Int, next: Boolean): Int? {
    val sorted = offsets.values.sorted()
    return if (next) sorted.firstOrNull { it > current + 1 } else sorted.lastOrNull { it < current - 1 }
}
