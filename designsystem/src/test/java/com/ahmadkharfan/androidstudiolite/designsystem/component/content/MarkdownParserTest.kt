package com.ahmadkharfan.androidstudiolite.designsystem.component.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `parses headings lists and task items`() {
        val md = """
            ## Plan
            - [ ] Add screen
            - [x] Done already
            1. First
            2. Second
        """.trimIndent()
        val blocks = MarkdownParser.parse(md)
        assertTrue(blocks[0] is MdBlock.Heading)
        assertEquals(2, (blocks[0] as MdBlock.Heading).level)
        val tasks = blocks[1] as MdBlock.ListBlock
        assertEquals(false, tasks.ordered)
        assertEquals(false, tasks.items[0].checked)
        assertEquals(true, tasks.items[1].checked)
        val ordered = blocks[2] as MdBlock.ListBlock
        assertTrue(ordered.ordered)
        assertEquals(2, ordered.items.size)
    }

    @Test
    fun `parses fenced code block`() {
        val md = """
            Intro
            ```kotlin
            fun main() {}
            ```
            Outro
        """.trimIndent()
        val blocks = MarkdownParser.parse(md)
        assertTrue(blocks[0] is MdBlock.Paragraph)
        val code = blocks[1] as MdBlock.Code
        assertEquals("kotlin", code.language)
        assertTrue(code.code.contains("fun main"))
        assertTrue(blocks[2] is MdBlock.Paragraph)
    }

    @Test
    fun `parses bold italic and leaves inline for renderer`() {
        val blocks = MarkdownParser.parse("Hello **world** and *italics*")
        assertEquals(1, blocks.size)
        assertEquals("Hello **world** and *italics*", (blocks[0] as MdBlock.Paragraph).text)
    }
}
