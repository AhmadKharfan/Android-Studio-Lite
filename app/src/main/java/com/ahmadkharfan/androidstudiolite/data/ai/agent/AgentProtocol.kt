package com.ahmadkharfan.androidstudiolite.data.ai.agent

import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** One decoded step of the agent conversation: either a batch of tool calls or a final answer. */
sealed interface AgentTurn {
    data class Actions(val thought: String?, val actions: List<AgentAction>) : AgentTurn
    data class Final(val text: String) : AgentTurn
}

/**
 * The provider-agnostic tool protocol. The model is told to answer with a single JSON object per turn:
 * either `{"actions": [...]}` to use file tools, or `{"final": "..."}` when done. This keeps one code
 * path across OpenAI/Anthropic/Gemini/DeepSeek/Grok without proprietary function-calling.
 */
object AgentProtocol {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun systemPrompt(
        userInstructions: String,
        projectOutline: String,
        activeFilePath: String?,
        mode: ChatMode = ChatMode.AGENT,
    ): String {
        val instructions = userInstructions.trim()
        val readOnly = mode != ChatMode.AGENT
        return buildString {
            when (mode) {
                ChatMode.AGENT -> appendLine(
                    "You are an autonomous Android coding agent embedded in Android Studio Lite. You can read " +
                        "and modify the user's open project by calling file tools. Prefer Kotlin and Jetpack Compose.",
                )
                ChatMode.ASK -> appendLine(
                    "You are a READ-ONLY Android assistant embedded in Android Studio Lite. You answer questions " +
                        "about the user's open project. You may inspect files but MUST NOT modify anything. " +
                        "Prefer Kotlin and Jetpack Compose.",
                )
                ChatMode.PLAN -> appendLine(
                    "You are a READ-ONLY planning assistant embedded in Android Studio Lite. You investigate the " +
                        "user's open project and produce a clear, step-by-step implementation plan. You MUST NOT " +
                        "modify anything. Prefer Kotlin and Jetpack Compose.",
                )
            }
            appendLine()
            appendLine("RESPONSE FORMAT — reply with EXACTLY ONE JSON object and nothing else. Two shapes:")
            appendLine("1. To use tools:")
            appendLine("""   {"thought":"short reasoning","actions":[{"tool":"read_file","path":"app/src/main/AndroidManifest.xml"}]}""")
            appendLine("2. When the task is complete (or you only need to answer in prose):")
            appendLine("""   {"thought":"done","final":"Summary of what you did."}""")
            appendLine()
            appendLine("TOOLS (all paths are project-relative, use forward slashes):")
            appendLine("- list_dir   {\"tool\":\"list_dir\",\"path\":\"app/src/main\"}")
            appendLine("- read_file  {\"tool\":\"read_file\",\"path\":\"...\"}")
            appendLine("- search     {\"tool\":\"search\",\"query\":\"text or filename\"}")
            if (!readOnly) {
                appendLine("- create_file{\"tool\":\"create_file\",\"path\":\"...\",\"content\":\"full file text\"}")
                appendLine("- create_dir {\"tool\":\"create_dir\",\"path\":\"...\"}")
                appendLine("- edit_file  {\"tool\":\"edit_file\",\"path\":\"...\",\"content\":\"COMPLETE new file text\"}")
                appendLine("- rename     {\"tool\":\"rename\",\"path\":\"...\",\"new_name\":\"NewName.kt\"}")
                appendLine("- move       {\"tool\":\"move\",\"path\":\"...\",\"new_parent\":\"dir/path\"}")
                appendLine("- delete     {\"tool\":\"delete\",\"path\":\"...\"}")
            }
            appendLine()
            appendLine("RULES:")
            when (mode) {
                ChatMode.AGENT -> {
                    appendLine("- edit_file and create_file require the ENTIRE file content, never a diff or partial snippet.")
                    appendLine("- Explore with read_file/list_dir before editing so your changes fit the existing code.")
                    appendLine("- You may batch several actions in one turn; they run in order.")
                    appendLine("- After tool results come back, continue until the task is done, then send a \"final\".")
                }
                ChatMode.ASK -> {
                    appendLine("- You are read-only: only list_dir, read_file, and search are available. Never edit files.")
                    appendLine("- Explore as needed, then answer the user's question in a \"final\" reply.")
                }
                ChatMode.PLAN -> {
                    appendLine("- You are read-only: only list_dir, read_file, and search are available. Never edit files.")
                    appendLine("- Investigate the relevant code, then deliver a numbered, step-by-step plan in a \"final\" reply.")
                }
            }
            appendLine("- Never wrap the JSON in markdown fences or add prose outside the JSON object.")
            if (instructions.isNotEmpty()) {
                appendLine()
                appendLine("USER INSTRUCTIONS:")
                appendLine(instructions)
            }
            if (activeFilePath != null) {
                appendLine()
                appendLine("The user currently has this file open: $activeFilePath")
            }
            appendLine()
            appendLine("PROJECT FILES (partial):")
            append(projectOutline)
        }
    }

    fun parse(raw: String): AgentTurn {
        val jsonText = extractJson(raw) ?: return AgentTurn.Final(raw.trim())
        val root = runCatching { json.parseToJsonElement(jsonText).jsonObject }.getOrNull()
            ?: return AgentTurn.Final(raw.trim())

        val final = root["final"]?.stringOrNull()
        if (!final.isNullOrBlank()) return AgentTurn.Final(final)

        val thought = root["thought"]?.stringOrNull()
        val actionsEl = root["actions"] as? JsonArray
        if (actionsEl != null) {
            val actions = actionsEl.mapNotNull { it.toActionOrNull() }
            if (actions.isNotEmpty()) return AgentTurn.Actions(thought, actions)
        }
        // Single inline action (no wrapper array).
        root.toActionOrNull()?.let { return AgentTurn.Actions(thought, listOf(it)) }

        return AgentTurn.Final(thought?.takeIf { it.isNotBlank() } ?: raw.trim())
    }

    private fun JsonElement.toActionOrNull(): AgentAction? {
        val obj = this as? JsonObject ?: return null
        val tool = obj["tool"]?.stringOrNull() ?: return null
        fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { obj[it]?.stringOrNull() }
        return when (tool) {
            "list_dir" -> AgentAction.ListDir(str("path", "dir") ?: ".")
            "read_file" -> str("path", "file")?.let { AgentAction.ReadFile(it) }
            "search" -> str("query", "q", "text")?.let { AgentAction.Search(it) }
            "create_file" -> str("path", "file")?.let { AgentAction.CreateFile(it, str("content", "text") ?: "") }
            "create_dir", "create_directory", "mkdir" -> str("path", "dir")?.let { AgentAction.CreateDir(it) }
            "edit_file", "write_file", "update_file" ->
                str("path", "file")?.let { AgentAction.EditFile(it, str("content", "text") ?: "") }
            "rename" -> str("path", "file")?.let { p ->
                str("new_name", "newName", "name")?.let { AgentAction.Rename(p, it) }
            }
            "move" -> str("path", "file")?.let { p ->
                str("new_parent", "newParent", "dest", "destination", "to")?.let { AgentAction.Move(p, it) }
            }
            "delete", "remove" -> str("path", "file")?.let { AgentAction.Delete(it) }
            else -> null
        }
    }

    private fun JsonElement.stringOrNull(): String? = runCatching { jsonPrimitive.contentOrNull }.getOrNull()

    /** Pulls the first balanced JSON object out of the reply, tolerating fences or surrounding prose. */
    private fun extractJson(raw: String): String? {
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(raw)?.groupValues?.get(1)?.trim()
        val candidate = fenced ?: raw
        val start = candidate.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until candidate.length) {
            val c = candidate[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return candidate.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
