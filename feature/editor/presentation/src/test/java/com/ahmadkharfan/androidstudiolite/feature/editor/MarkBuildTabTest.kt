package com.ahmadkharfan.androidstudiolite.feature.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkBuildTabTest {

    private val tabs = listOf(
        BottomPanelTabUiModel("build", "Build Output", "hammer"),
        BottomPanelTabUiModel("term", "Terminal", "terminal"),
    )

    @Test
    fun updatesOnlyTheBuildTab() {
        val result = markBuildTab(tabs, error = true, count = 3)
        val build = result.first { it.id == "build" }
        val terminal = result.first { it.id == "term" }

        assertEquals(true, build.error)
        assertEquals(3, build.count)
        assertEquals(tabs[1], terminal)
    }

    @Test
    fun clearsErrorAndCountWhenReset() {
        val marked = markBuildTab(tabs, error = true, count = 3)
        val cleared = markBuildTab(marked, error = false, count = null)
        val build = cleared.first { it.id == "build" }

        assertEquals(false, build.error)
        assertEquals(null, build.count)
    }
}
