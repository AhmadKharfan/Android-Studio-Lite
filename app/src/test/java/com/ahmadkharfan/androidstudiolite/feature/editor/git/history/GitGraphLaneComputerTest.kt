package com.ahmadkharfan.androidstudiolite.feature.editor.git.history

import com.ahmadkharfan.androidstudiolite.domain.model.GitCommitSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GitGraphLaneComputerTest {

    @Test
    fun `linear history remains in one lane`() {
        val page = GitGraphLaneComputer().layout(
            listOf(
                commit("c", "b"),
                commit("b", "a"),
                commit("a"),
            ),
        )
        assertTrue(page.rows.all { it.lane == 0 && it.laneCount == 1 })
    }

    @Test
    fun `branch and merge produce fork and join edges`() {
        val page = GitGraphLaneComputer().layout(
            listOf(
                commit("merge", "left", "right"),
                commit("left", "base"),
                commit("right", "base"),
                commit("base"),
            ),
        )
        assertTrue(page.rows.all { it.laneCount > 1 })
        val edges = page.rows.flatMap { it.edges }
        assertTrue(edges.isNotEmpty())
        assertTrue(edges.any { it.fromLane != it.toLane })
        assertEquals(0, page.rows.first().lane)
    }

    @Test
    fun `two concurrent branches keep separate active lanes`() {
        val page = GitGraphLaneComputer().layout(
            listOf(
                commit("tip-a", "a"),
                commit("tip-b", "b"),
                commit("a", "root"),
                commit("b", "root"),
                commit("root"),
            ),
        )
        val activeTips = page.rows.take(2)
        assertTrue(activeTips[0].lane != activeTips[1].lane)
        assertTrue(activeTips.all { it.laneCount >= 2 })
    }

    @Test
    fun `cursor preserves lane continuity across pages`() {
        val commits = listOf(
            commit("merge", "left", "right"),
            commit("left", "left-parent"),
            commit("right", "root"),
            commit("left-parent", "root"),
            commit("root"),
        )
        val first = GitGraphLaneComputer(maxLanes = 5).layout(commits.take(2))
        val second = GitGraphLaneComputer(maxLanes = 5).layout(commits.drop(2), first.nextCursor)
        assertEquals(first.rows.last().lane, second.rows.first().lane)
    }

    @Test
    fun `overflow branches collapse into the bounded final lane`() {
        val page = GitGraphLaneComputer(maxLanes = 3).layout(
            listOf(
                commit("tip-1", "parent-1"),
                commit("tip-2", "parent-2"),
                commit("tip-3", "parent-3"),
                commit("tip-4", "parent-4"),
            ),
        )
        assertTrue(page.rows.all { it.lane < 3 })
        assertTrue(page.rows.any { it.lane == 2 })
    }

    private fun commit(id: String, vararg parents: String) = GitCommitSummary(
        id = id,
        shortId = id.take(7),
        message = id,
        fullMessage = id,
        authorName = "Test",
        authorEmail = "test@example.com",
        authorTimeMillis = 0L,
        parents = parents.toList(),
    )
}
