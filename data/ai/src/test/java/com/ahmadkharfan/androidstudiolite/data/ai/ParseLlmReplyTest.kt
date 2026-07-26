package com.ahmadkharfan.androidstudiolite.data.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class ParseLlmReplyTest {

    @Test
    fun plainTextReturnsNoSnippet() {
        val reply = parseLlmReply("Explain this composable.")
        assertEquals("Explain this composable.", reply.text)
        assertNull(reply.codeSnippet)
    }

    @Test
    fun fencedBlockExtractsSnippet() {
        val dollar = "$"
        val raw = """
            Here's a helper:
            ```kotlin
            fun greet(name: String) = "Hello, ${dollar}name"
            ```
        """.trimIndent()
        val reply = parseLlmReply(raw)
        assertEquals("Here's a helper:", reply.text)
        assertNotNull(reply.codeSnippet)
        assertEquals("kotlin", reply.codeSnippet?.language)
        assertEquals("fun greet(name: String) = \"Hello, ${dollar}name\"", reply.codeSnippet?.code)
    }
}
