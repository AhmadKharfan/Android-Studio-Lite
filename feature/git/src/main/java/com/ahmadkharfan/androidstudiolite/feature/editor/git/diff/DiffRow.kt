package com.ahmadkharfan.androidstudiolite.feature.editor.git.diff

import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffHunk
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffLine

internal sealed interface DiffRow {
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

internal fun targetOffset(offsets: Map<Int, Int>, current: Int, next: Boolean): Int? {
    val sorted = offsets.values.sorted()
    return if (next) sorted.firstOrNull { it > current + 1 } else sorted.lastOrNull { it < current - 1 }
}
