package com.ahmadkharfan.androidstudiolite.data.ai.agent

import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction

/** Repairs and validates Kotlin file payloads from LLM tool calls before writing to disk. */
internal object AgentContentSanitizer {

    fun sanitizeAction(action: AgentAction): AgentAction = when (action) {
        is AgentAction.CreateFile -> action.copy(content = sanitizeFileContent(action.path, action.content))
        is AgentAction.EditFile -> action.copy(content = sanitizeFileContent(action.path, action.content))
        else -> action
    }

    fun sanitizeFileContent(path: String, content: String): String {
        if (!isKotlinPath(path)) return content
        var text = content.replace("\r\n", "\n").replace('\r', '\n')
        // Common JSON/stream glitches seen from OpenAI models.
        text = text.replace("*nimport", "\nimport")
        text = text.replace(Regex("""\.\\*nimport"""), ".\nimport")
        text = text.replace(Regex("""\*n(?=import|package|@|fun |class |val |var |//)"""), "\n")
        text = text.replace(Regex("""\n{3,}"""), "\n\n")
        return text.trimEnd() + "\n"
    }

    fun validateFileContent(path: String, content: String): List<String> {
        if (!isKotlinPath(path)) return emptyList()
        val warnings = ArrayList<String>()
        if (content.isBlank()) {
            warnings.add("empty content")
        }
        if (!content.contains("package ") && !content.contains("import ") && !content.contains("fun ")) {
            warnings.add("missing kotlin structure")
        }
        val openBraces = content.count { it == '{' }
        val closeBraces = content.count { it == '}' }
        if (openBraces != closeBraces) {
            warnings.add("unbalanced braces ($openBraces open, $closeBraces close)")
        }
        val openParens = content.count { it == '(' }
        val closeParens = content.count { it == ')' }
        if (openParens != closeParens) {
            warnings.add("unbalanced parentheses ($openParens open, $closeParens close)")
        }
        return warnings
    }

    private fun isKotlinPath(path: String): Boolean =
        path.endsWith(".kt", ignoreCase = true) || path.endsWith(".kts", ignoreCase = true)
}
