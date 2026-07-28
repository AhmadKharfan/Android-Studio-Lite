package com.ahmadkharfan.androidstudiolite.feature.editor.git

import com.ahmadkharfan.androidstudiolite.domain.model.GitFileStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitPanelUiStateTest {
    @Test
    fun `change count sums all change lists and is zero when empty`() {
        val state = GitPanelUiState()

        assertEquals(0, state.changeCount)
        assertEquals(
            6,
            state.copy(
                stagedChanges = listOf(change("staged.kt")),
                unstagedChanges = listOf(change("unstaged-1.kt"), change("unstaged-2.kt")),
                untrackedChanges = listOf(
                    change("untracked-1.kt"),
                    change("untracked-2.kt"),
                    change("untracked-3.kt"),
                ),
            ).changeCount,
        )
    }

    @Test
    fun `chip row appears for each independent trigger and stays hidden without one`() {
        val state = GitPanelUiState().copy(behind = null, ahead = null)

        assertFalse(state.hasChipRow)
        assertTrue(state.copy(selectedPaths = setOf("selected.kt")).hasChipRow)
        assertTrue(state.copy(stagedChanges = listOf(change("changed.kt"))).hasChipRow)
        assertTrue(state.copy(behind = 1).hasChipRow)
        assertTrue(state.copy(ahead = 1).hasChipRow)
    }

    @Test
    fun `section selection distinguishes none some and all selected paths`() {
        val paths = listOf("first.kt", "second.kt")
        val state = GitPanelUiState()

        assertEquals(
            GitSectionSelection(allSelected = false, indeterminate = false),
            state.sectionSelection(paths),
        )
        assertEquals(
            GitSectionSelection(allSelected = false, indeterminate = true),
            state.copy(selectedPaths = setOf("first.kt")).sectionSelection(paths),
        )
        assertEquals(
            GitSectionSelection(allSelected = true, indeterminate = false),
            state.copy(selectedPaths = paths.toSet()).sectionSelection(paths),
        )
    }

    @Test
    fun `empty section paths remain all selected and not indeterminate`() {
        assertEquals(
            GitSectionSelection(allSelected = true, indeterminate = false),
            GitPanelUiState().sectionSelection(emptyList()),
        )
    }

    private fun change(path: String) = GitChangeUiModel(
        path = path,
        status = GitFileStatus.MODIFIED,
    )
}
