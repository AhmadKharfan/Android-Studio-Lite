package com.ahmadkharfan.androidstudiolite.feature.git.history

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
        val lanes = cursor.lanes.map { it.toMutableList() }.toMutableList()
        val rows = commits.map { layoutRow(it, lanes) }
        return GitGraphPage(rows, GitGraphCursor(lanes.map { it.toList() }))
    }

    private fun layoutRow(
        commit: GitCommitSummary,
        lanes: MutableList<MutableList<String>>,
    ): GitGraphRow {
        val location = locateCommit(commit.id, lanes)
        val before = lanes.map { it.toList() }
        lanes[location.lane].remove(commit.id)
        if (lanes[location.lane].isEmpty()) lanes.removeAt(location.lane)
        addParentLanes(commit.parents, location.lane, lanes)
        return GitGraphRow(
            commitId = commit.id,
            lane = location.lane.coerceAtMost(maxLanes - 1),
            laneCount = maxOf(before.size, lanes.size, 1).coerceAtMost(maxLanes),
            edges = graphEdges(commit, location.lane, before, lanes),
            collapsed = before.lastOrNull()?.size.orZero() > 1 || lanes.lastOrNull()?.size.orZero() > 1,
            hasIncoming = location.hasIncoming,
        )
    }

    private fun locateCommit(
        commitId: String,
        lanes: MutableList<MutableList<String>>,
    ): LaneLocation {
        val existingLane = lanes.indexOfFirst { commitId in it }
        if (existingLane >= 0) return LaneLocation(existingLane, hasIncoming = true)
        val lane = if (lanes.size < maxLanes) lanes.size else maxLanes - 1
        if (lane == lanes.size) lanes += mutableListOf(commitId) else lanes[lane] += commitId
        return LaneLocation(lane, hasIncoming = false)
    }

    private fun addParentLanes(
        parents: List<String>,
        commitLane: Int,
        lanes: MutableList<MutableList<String>>,
    ) {
        parents.forEachIndexed { index, parent ->
            if (lanes.any { parent in it }) return@forEachIndexed
            val requested = (commitLane + index).coerceAtMost(maxLanes - 1)
            if (requested in lanes.indices && lanes.size < maxLanes) lanes.add(requested, mutableListOf(parent))
            else if (lanes.size < maxLanes) lanes += mutableListOf(parent)
            else lanes[lanes.lastIndex] += parent
        }
    }

    private fun graphEdges(
        commit: GitCommitSummary,
        commitLane: Int,
        before: List<List<String>>,
        lanes: List<List<String>>,
    ): List<GitGraphEdge> = buildList {
        before.forEachIndexed { from, ids ->
            ids.filterNot { it == commit.id }.forEach { id ->
                lanes.indexOfFirst { id in it }.takeIf { it in lanes.indices }?.let { add(GitGraphEdge(from, it)) }
            }
        }
        commit.parents.forEach { parent ->
            lanes.indexOfFirst { parent in it }.takeIf { it in lanes.indices }?.let { add(GitGraphEdge(commitLane, it)) }
        }
    }.distinct()

    private data class LaneLocation(val lane: Int, val hasIncoming: Boolean)

    private fun Int?.orZero() = this ?: 0
}
