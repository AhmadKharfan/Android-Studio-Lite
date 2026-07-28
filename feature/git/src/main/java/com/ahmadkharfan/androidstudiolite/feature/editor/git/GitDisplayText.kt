package com.ahmadkharfan.androidstudiolite.feature.editor.git

import com.ahmadkharfan.androidstudiolite.domain.model.GitCommitChangeType
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffHunk
import com.ahmadkharfan.androidstudiolite.domain.model.GitRepositoryState
import com.ahmadkharfan.androidstudiolite.domain.model.GitSubmoduleStatus

fun hunkHeader(hunk: GitDiffHunk): String =
    "@@ -${hunk.oldStart},${hunk.oldCount} +${hunk.newStart},${hunk.newCount} @@"

fun stashLabel(index: Int): String = "stash@{$index}"

fun blameGutterText(lineNo: Int, shortId: String, authorName: String): String =
    "${lineNo.toString().padStart(4)}  ${shortId.padEnd(7)}  ${authorName.take(12).padEnd(12)}  "

fun behindLabel(count: Int): String = "↓$count"

fun aheadLabel(count: Int): String = "↑$count"

fun submoduleStatusLabel(status: GitSubmoduleStatus): String =
    status.name.lowercase().replace('_', ' ')

fun repositoryStateSentenceLabel(state: GitRepositoryState): String = state.name.lowercase()

fun repositoryStateLabel(state: GitRepositoryState): String =
    state.name.lowercase().replaceFirstChar(Char::uppercase)

fun changeTypeLabel(type: GitCommitChangeType): String = type.name.first().toString()
