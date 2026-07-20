package com.ahmadkharfan.androidstudiolite.feature.editor.git.history

import com.ahmadkharfan.androidstudiolite.domain.model.GitCommitSummary

data class GitGraphCursor(val lanes: List<List<String>> = emptyList())

data class GitGraphEdge(val fromLane: Int, val toLane: Int)

data class GitGraphRow(
    val commitId: String,
    val lane: Int,
    val laneCount: Int,
    val edges: List<GitGraphEdge>,
    val collapsed: Boolean,
    val hasIncoming: Boolean,
)

data class GitGraphPage(val rows: List<GitGraphRow>, val nextCursor: GitGraphCursor)

class GitGraphLaneComputer(private val maxLanes: Int = 8) {
    init { require(maxLanes >= 2) }

    fun layout(commits: List<GitCommitSummary>, cursor: GitGraphCursor = GitGraphCursor()): GitGraphPage {
        var lanes = cursor.lanes.map { it.toMutableList() }.toMutableList()
        val rows = commits.map { commit ->
            var lane = lanes.indexOfFirst { commit.id in it }
            val hasIncoming = lane >= 0
            if (lane < 0) {
                lane = if (lanes.size < maxLanes) lanes.size else maxLanes - 1
                if (lane == lanes.size) lanes += mutableListOf(commit.id) else lanes[lane] += commit.id
            }
            val before = lanes.map { it.toList() }
            lanes[lane].remove(commit.id)
            if (lanes[lane].isEmpty()) lanes.removeAt(lane)

            commit.parents.forEachIndexed { index, parent ->
                if (lanes.any { parent in it }) return@forEachIndexed
                val requested = (lane + index).coerceAtMost(maxLanes - 1)
                if (requested < lanes.size && lanes.size < maxLanes) lanes.add(requested, mutableListOf(parent))
                else if (lanes.size < maxLanes) lanes += mutableListOf(parent)
                else lanes[maxLanes - 1] += parent
            }

            val edges = buildList {
                before.forEachIndexed { from, ids ->
                    ids.filterNot { it == commit.id }.forEach { id ->
                        lanes.indexOfFirst { id in it }.takeIf { it >= 0 }?.let { add(GitGraphEdge(from, it)) }
                    }
                }
                commit.parents.forEach { parent ->
                    lanes.indexOfFirst { parent in it }.takeIf { it >= 0 }?.let { add(GitGraphEdge(lane, it)) }
                }
            }.distinct()
            GitGraphRow(
                commitId = commit.id,
                lane = lane.coerceAtMost(maxLanes - 1),
                laneCount = maxOf(before.size, lanes.size, 1).coerceAtMost(maxLanes),
                edges = edges,
                collapsed = before.lastOrNull()?.size.orZero() > 1 || lanes.lastOrNull()?.size.orZero() > 1,
                hasIncoming = hasIncoming,
            )
        }
        return GitGraphPage(rows, GitGraphCursor(lanes.map { it.toList() }))
    }

    private fun Int?.orZero() = this ?: 0
}
