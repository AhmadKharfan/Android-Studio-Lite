package com.ahmadkharfan.androidstudiolite.feature.editor.git.history

import com.ahmadkharfan.androidstudiolite.domain.model.GitCommitSummary
import org.junit.Assert.assertEquals
import org.junit.Test

class GitGraphLayoutCharacterizationTest {

    @Test
    fun `merge pagination retains complete row and cursor layout`() {
        val commits = listOf(
            commit("merge", "left", "right", "overflow"),
            commit("left", "base"),
            commit("right", "base"),
            commit("overflow", "base"),
            commit("base"),
        )
        val computer = GitGraphLaneComputer(maxLanes = 3)
        val first = computer.layout(commits.take(2))
        val second = computer.layout(commits.drop(2), first.nextCursor)

        assertEquals(
            GitGraphPage(
                rows = listOf(
                    row("merge", 0, 3, edges(0 to 0, 0 to 1, 0 to 2), hasIncoming = false),
                    row("left", 0, 3, edges(1 to 1, 2 to 2, 0 to 0)),
                ),
                nextCursor = GitGraphCursor(listOf(listOf("base"), listOf("right"), listOf("overflow"))),
            ),
            first,
        )
        assertEquals(
            GitGraphPage(
                rows = listOf(
                    row("right", 1, 3, edges(0 to 0, 2 to 1, 1 to 0)),
                    row("overflow", 1, 2, edges(0 to 0, 1 to 0)),
                    row("base", 0, 1, emptyList()),
                ),
                nextCursor = GitGraphCursor(),
            ),
            second,
        )
    }

    private fun row(
        id: String,
        lane: Int,
        laneCount: Int,
        edges: List<GitGraphEdge>,
        hasIncoming: Boolean = true,
    ) = GitGraphRow(id, lane, laneCount, edges, collapsed = false, hasIncoming = hasIncoming)

    private fun edges(vararg lanes: Pair<Int, Int>) = lanes.map { (from, to) -> GitGraphEdge(from, to) }

    private fun commit(id: String, vararg parents: String) = GitCommitSummary(
        id = id,
        shortId = id,
        message = id,
        fullMessage = id,
        authorName = "Test",
        authorEmail = "test@example.com",
        authorTimeMillis = 0L,
        parents = parents.toList(),
    )
}
