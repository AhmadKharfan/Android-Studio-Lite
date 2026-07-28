package com.ahmadkharfan.androidstudiolite.feature.editor.git.diff

import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffHunk
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffKind
import com.ahmadkharfan.androidstudiolite.domain.model.GitDiffLine
import org.junit.Assert.assertEquals
import org.junit.Test

class DiffAlignmentTest {
    @Test
    fun `context lines pair with themselves`() {
        val first = line(GitDiffKind.CONTEXT, "first", oldNo = 1, newNo = 1)
        val second = line(GitDiffKind.CONTEXT, "second", oldNo = 2, newNo = 2)

        assertEquals(
            listOf(DiffRow.Paired(first, first), DiffRow.Paired(second, second)),
            hunk(first, second).alignedRows(),
        )
    }

    @Test
    fun `removed lines pair with added lines positionally`() {
        val firstRemoved = line(GitDiffKind.REMOVED, "old one", oldNo = 1)
        val secondRemoved = line(GitDiffKind.REMOVED, "old two", oldNo = 2)
        val firstAdded = line(GitDiffKind.ADDED, "new one", newNo = 1)
        val secondAdded = line(GitDiffKind.ADDED, "new two", newNo = 2)

        assertEquals(
            listOf(
                DiffRow.Paired(firstRemoved, firstAdded),
                DiffRow.Paired(secondRemoved, secondAdded),
            ),
            hunk(firstRemoved, secondRemoved, firstAdded, secondAdded).alignedRows(),
        )
    }

    @Test
    fun `unequal runs pad the shorter side with null`() {
        val firstRemoved = line(GitDiffKind.REMOVED, "old one", oldNo = 1)
        val secondRemoved = line(GitDiffKind.REMOVED, "old two", oldNo = 2)
        val thirdRemoved = line(GitDiffKind.REMOVED, "old three", oldNo = 3)
        val added = line(GitDiffKind.ADDED, "new one", newNo = 1)

        assertEquals(
            listOf(
                DiffRow.Paired(firstRemoved, added),
                DiffRow.Paired(secondRemoved, null),
                DiffRow.Paired(thirdRemoved, null),
            ),
            hunk(firstRemoved, secondRemoved, thirdRemoved, added).alignedRows(),
        )
    }

    @Test
    fun `modified lines are treated as added`() {
        val removed = line(GitDiffKind.REMOVED, "old", oldNo = 1)
        val modified = line(GitDiffKind.MODIFIED, "modified", newNo = 1)

        assertEquals(
            listOf(DiffRow.Paired(removed, modified)),
            hunk(removed, modified).alignedRows(),
        )
    }

    @Test
    fun `context line flushes a pending run before pairing with itself`() {
        val removed = line(GitDiffKind.REMOVED, "old", oldNo = 1)
        val context = line(GitDiffKind.CONTEXT, "same", oldNo = 2, newNo = 1)
        val added = line(GitDiffKind.ADDED, "new", newNo = 2)

        assertEquals(
            listOf(
                DiffRow.Paired(removed, null),
                DiffRow.Paired(context, context),
                DiffRow.Paired(null, added),
            ),
            hunk(removed, context, added).alignedRows(),
        )
    }

    @Test
    fun `empty hunk yields no rows`() {
        assertEquals(emptyList<DiffRow.Paired>(), hunk().alignedRows())
    }

    private fun hunk(vararg lines: GitDiffLine) = GitDiffHunk(
        oldStart = 1,
        oldCount = lines.count { it.oldNo != null },
        newStart = 1,
        newCount = lines.count { it.newNo != null },
        lines = lines.toList(),
    )

    private fun line(
        kind: GitDiffKind,
        text: String,
        oldNo: Int? = null,
        newNo: Int? = null,
    ) = GitDiffLine(kind = kind, text = text, oldNo = oldNo, newNo = newNo)
}
