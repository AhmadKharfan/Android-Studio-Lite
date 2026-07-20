package com.ahmadkharfan.androidstudiolite.data.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentContentSanitizerTest {

    @Test
    fun `sanitizeFileContent repairs star-n import glitches`() {
        val raw = "package com.example*nimport androidx.compose.material.Text"
        val fixed = AgentContentSanitizer.sanitizeFileContent("app/Foo.kt", raw)
        assertTrue(fixed.contains("\nimport androidx.compose.material.Text"))
    }

    @Test
    fun `sanitizeFileContent normalizes newlines and trailing newline`() {
        val raw = "package com.example\r\n\r\nfun main() {}"
        val fixed = AgentContentSanitizer.sanitizeFileContent("app/Foo.kt", raw)
        assertTrue(fixed.endsWith("\n"))
        assertTrue(!fixed.contains("\r"))
    }

    @Test
    fun `validateFileContent warns on unbalanced braces`() {
        val warnings = AgentContentSanitizer.validateFileContent(
            "app/Foo.kt",
            "package com.example\nfun main() {",
        )
        assertTrue(warnings.any { it.contains("unbalanced braces") })
    }
}
