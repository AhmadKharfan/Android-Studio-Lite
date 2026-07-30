package com.ahmadkharfan.androidstudiolite.feature.editor.filetree

import com.ahmadkharfan.androidstudiolite.domain.model.FileNode
import com.ahmadkharfan.androidstudiolite.feature.editor.EditorFileNodeUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EditorFileTreeNavigationTest {

    private fun dir(id: String, name: String, children: List<FileNode>) = FileNode(id, name, children)
    private fun file(id: String, name: String) = FileNode(id, name, children = null)

    @Test
    fun pathToDefaultFilePrefersMainActivity() {
        val tree = listOf(
            dir("/p/app", "app", listOf(file("/p/app/Other.kt", "Other.kt"))),
            dir("/p/src", "src", listOf(file("/p/src/MainActivity.kt", "MainActivity.kt"))),
        )
        assertEquals("/p/src/MainActivity.kt", firstOpenableFile(tree)?.id)
    }

    @Test
    fun pathToDefaultFileFallsBackToAnyCodeFileThenAnyFile() {
        val codeTree = listOf(dir("/p", "p", listOf(file("/p/readme.txt", "readme.txt"), file("/p/A.kt", "A.kt"))))
        assertEquals("/p/A.kt", firstOpenableFile(codeTree)?.id)

        val noCodeTree = listOf(dir("/p", "p", listOf(file("/p/readme.txt", "readme.txt"))))
        assertEquals("/p/readme.txt", firstOpenableFile(noCodeTree)?.id)
    }

    @Test
    fun defaultExpandedIdsIncludeTopLevelDirsAndTrailToDefaultFile() {
        val tree = listOf(
            dir("/p/app", "app", listOf(dir("/p/app/src", "src", listOf(file("/p/app/src/MainActivity.kt", "MainActivity.kt"))))),
            dir("/p/docs", "docs", listOf(file("/p/docs/readme.txt", "readme.txt"))),
        )
        assertEquals(setOf("/p/app", "/p/docs", "/p/app/src"), defaultExpandedIds(tree))
    }

    @Test
    fun isCodeFileMatchesKnownExtensionsCaseInsensitively() {
        assertTrue(isCodeFile("Main.KT"))
        assertTrue(isCodeFile("layout.xml"))
        assertFalse(isCodeFile("photo.png"))
        assertFalse(isCodeFile("noext"))
    }

    @Test
    fun findFileTreeNodeSearchesNestedChildren() {
        val tree = listOf(
            EditorFileNodeUiModel(
                id = "/p",
                name = "p",
                children = listOf(EditorFileNodeUiModel(id = "/p/A.kt", name = "A.kt")),
            ),
        )
        assertEquals("A.kt", findFileTreeNode(tree, "/p/A.kt")?.name)
        assertNull(findFileTreeNode(tree, "/p/missing"))
    }

    @Test
    fun isValidFileTreeNameRejectsSeparatorsAndDotEntries() {
        assertTrue(isValidFileTreeName("Main.kt"))
        assertFalse(isValidFileTreeName(""))
        assertFalse(isValidFileTreeName(" "))
        assertFalse(isValidFileTreeName("a/b"))
        assertFalse(isValidFileTreeName("a\\b"))
        assertFalse(isValidFileTreeName("."))
        assertFalse(isValidFileTreeName(".."))
    }

    @Test
    fun isSameOrChildDistinguishesSiblingsFromDescendants() {
        assertTrue(isSameOrChild("/p/src", "/p/src"))
        assertTrue(isSameOrChild("/p/src", "/p/src/Main.kt"))
        assertFalse(isSameOrChild("/p/src", "/p/src-other/Main.kt"))
        assertFalse(isSameOrChild("/p/src", "/p/other"))
    }

    @Test
    fun renamedPathForRewritesTheEntryAndNestedChildren() {
        assertEquals("/p/new", renamedPathFor("/p/old", "/p/old", "/p/new"))
        assertEquals("/p/new/A.kt", renamedPathFor("/p/old/A.kt", "/p/old", "/p/new"))
    }

    @Test
    fun breadcrumbForIsProjectRelativeWithNameFallback() {
        assertEquals(listOf("src", "Main.kt"), breadcrumbFor("/p", "/p/src/Main.kt", "Main.kt"))
        assertEquals(listOf("Main.kt"), breadcrumbFor("/p", "/p/Main.kt", "Main.kt"))
        assertEquals(listOf("p", "Main.kt"), breadcrumbFor(null, "/p/Main.kt", "Main.kt"))
    }
}
