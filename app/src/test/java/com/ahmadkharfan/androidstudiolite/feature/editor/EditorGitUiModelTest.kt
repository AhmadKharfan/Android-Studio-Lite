package com.ahmadkharfan.androidstudiolite.feature.editor

import com.ahmadkharfan.androidstudiolite.domain.model.GitFileState
import com.ahmadkharfan.androidstudiolite.domain.model.GitFileStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitConflictInfo
import com.ahmadkharfan.androidstudiolite.domain.model.GitHeadState
import com.ahmadkharfan.androidstudiolite.domain.model.GitIndexStatus
import com.ahmadkharfan.androidstudiolite.domain.model.GitRepositoryState
import com.ahmadkharfan.androidstudiolite.domain.model.GitState
import com.ahmadkharfan.androidstudiolite.domain.model.GitWorktreeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EditorGitUiModelTest {
    @Test
    fun `branch status includes dirty marker and badge count`() {
        val model = GitState(
            files = listOf(
                GitFileState("staged.kt", indexStatus = GitIndexStatus.ADDED),
                GitFileState("changed.kt", worktreeStatus = GitWorktreeStatus.MODIFIED),
            ),
            headState = GitHeadState.Branch("main"),
        ).toEditorGitUiModel()

        assertEquals("main ●", model.statusText)
        assertEquals(2, model.pendingChangeCount)
        assertEquals("2", EditorUiState(gitPendingChangeCount = model.pendingChangeCount).gitBadge)
    }

    @Test
    fun `detached and repository operation labels are derived from git state`() {
        val detached = GitState(
            files = emptyList(),
            headState = GitHeadState.Detached("abc1234"),
        ).toEditorGitUiModel()
        val merging = GitState(
            files = emptyList(),
            headState = GitHeadState.Branch("feature"),
            repositoryState = GitRepositoryState.MERGING,
        ).toEditorGitUiModel()

        assertEquals("HEAD (abc1234)", detached.statusText)
        assertEquals("feature — Merging", merging.statusText)
    }

    @Test
    fun `non repository hides git chrome`() {
        val model = GitState(files = emptyList(), isRepository = false).toEditorGitUiModel()

        assertNull(model.statusText)
        assertEquals(0, model.pendingChangeCount)
        assertNull(EditorUiState().gitBadge)
    }

    @Test
    fun `conflict is the strongest file tree signal`() {
        val file = GitFileState(
            path = "conflict.kt",
            indexStatus = GitIndexStatus.ADDED,
            worktreeStatus = GitWorktreeStatus.MODIFIED,
            conflictStage = GitConflictInfo(stages = emptySet(), description = "both added"),
        )

        assertEquals(GitFileStatus.CONFLICTED, file.toEditorFileStatus())
    }
}
