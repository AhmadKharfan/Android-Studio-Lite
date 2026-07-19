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

    const val PARSE_FAILURE_MESSAGE = "I couldn't apply the changes. Please try again."

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun systemPrompt(
        userInstructions: String,
        projectOutline: String,
        activeFilePath: String?,
        mode: ChatMode = ChatMode.AGENT,
        kotlinSourcePackagePrefix: String? = null,
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
                        "modify anything. Prefer Kotlin and Jetpack Compose. Format the plan as markdown with " +
                        "headings and a task list using `- [ ]` checkboxes for each step.",
                )
            }
            appendLine()
            appendLine("RESPONSE FORMAT — reply with EXACTLY ONE JSON object and nothing else. Two shapes:")
            appendLine("1. To use tools:")
            appendLine("""   {"thought":"short reasoning","actions":[{"tool":"read_file","path":"app/src/main/AndroidManifest.xml"}]}""")
            appendLine("2. When the task is complete (or you only need to answer in prose):")
            appendLine("""   {"thought":"done","final":"Markdown summary. Use headings, lists, and - [ ] todos when planning."}""")
            appendLine()
            appendLine("Put short reasoning in \"thought\" (streamed to the user as Thinking). Put the user-facing " +
                "answer in \"final\". Never put the full answer only in thought.")
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
                    appendLine("- If the user asks you to implement, apply, build, create, fix, or otherwise change " +
                        "code, you MUST call file tools and actually edit files. Never answer with only prose.")
                    appendLine("- A plan may already exist earlier in this conversation — implement it now with tools.")
                    appendLine("- Put reasoning in \"thought\"; only send \"final\" after the edits are done.")
                    appendLine("- edit_file and create_file require the ENTIRE file content, never a diff or partial snippet.")
                    appendLine("- Explore with read_file/list_dir before editing so your changes fit the existing code.")
                    appendLine("- You may batch several actions in one turn; they run in order.")
                    appendLine("- After tool results come back, continue until the task is done, then send a \"final\".")
                }
                ChatMode.ASK -> {
                    appendLine("- You are read-only: only list_dir, read_file, and search are available. Never edit files.")
                    appendLine("- Explore as needed, then answer the user's question in a \"final\" reply with markdown.")
                }
                ChatMode.PLAN -> {
                    appendLine("- You are read-only: only list_dir, read_file, and search are available. Never edit files.")
                    appendLine("- Investigate the relevant code, then deliver a numbered / checkbox step-by-step plan " +
                        "in \"final\" as markdown (headings + `- [ ]` todos).")
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
            if (!kotlinSourcePackagePrefix.isNullOrBlank()) {
                appendLine()
                appendLine("KOTLIN SOURCE ROOT (use this prefix for all new Kotlin files):")
                appendLine(kotlinSourcePackagePrefix)
            }
            appendLine()
            appendLine("PROJECT FILES (partial):")
            append(projectOutline)
        }
    }

    /**
     * Parses a model turn for execution. When [preferActions] is true (Agent mode), a non-empty
     * `actions` array wins over `final` so the agent actually runs tools instead of dumping JSON.
     */
    fun parseExecutionTurn(raw: String, preferActions: Boolean = false): AgentTurn {
        val root = parseRootObject(raw)
        if (root != null) {
            val turn = turnFromRoot(root, preferActions)
            if (turn is AgentTurn.Actions || turn is AgentTurn.Final && !isParseFailureFinal(turn.text)) {
                return turn
            }
        }

        val salvaged = salvageActions(raw)
        if (preferActions && salvaged.isNotEmpty()) {
            return AgentTurn.Actions(extractThought(raw), salvaged)
        }

        return AgentTurn.Final(sanitizeDisplayText(raw))
    }

    private fun turnFromRoot(root: JsonObject, preferActions: Boolean): AgentTurn {
        val thought = root["thought"]?.stringOrNull()
        val actions = parseActions(root)

        if (preferActions && actions.isNotEmpty()) {
            return AgentTurn.Actions(thought, actions)
        }

        val final = root["final"]?.stringOrNull()
        if (!final.isNullOrBlank()) return AgentTurn.Final(final)

        if (actions.isNotEmpty()) return AgentTurn.Actions(thought, actions)
        root.toActionOrNull()?.let { return AgentTurn.Actions(thought, listOf(it)) }

        return AgentTurn.Final(thought?.takeIf { it.isNotBlank() } ?: PARSE_FAILURE_MESSAGE)
    }

    fun isUnparsedProtocolResponse(raw: String, finalText: String): Boolean {
        if (finalText == PARSE_FAILURE_MESSAGE) return true
        if (isTruncatedProtocolJson(raw)) return true
        return looksLikeProtocolJson(raw) &&
            parseRootObject(raw) == null &&
            salvageActions(raw).isEmpty()
    }

    fun jsonRetryPrompt(): String =
        "Your last reply was invalid or truncated JSON, so no file edits ran. " +
            "Reply with ONE valid JSON object only. Use escaped newlines (\\n) and quotes (\\\") " +
            "inside edit_file content. If the file is large, call edit_file with the complete file text anyway."

    fun implementationContinuePrompt(toolsRun: Int): String =
        "Your last reply was invalid or truncated JSON after $toolsRun tool step(s) already ran. " +
            "Continue implementing the remaining plan steps now. Reply with ONE valid JSON object " +
            "using file tools only — use escaped \\n and \\\" inside file content."

    fun implementPlanPrompt(planMarkdown: String): String =
        "Implement the plan below step by step using file tools. Put every new Kotlin file under " +
            "the KOTLIN SOURCE ROOT from the system prompt. Complete all checklist items, then send " +
            "a final markdown summary.\n\n$planMarkdown"

    fun reviewPlanPrompt(userInstructions: String? = null): String = buildString {
        append("Review the implementation plan above.")
        val focus = userInstructions?.trim()?.takeIf { it.isNotBlank() }
        if (focus != null) {
            appendLine()
            appendLine()
            appendLine("The user wants you to focus on:")
            append(focus)
        } else {
            append(" Identify gaps, risks, missing steps, and concrete improvements.")
        }
        append(" Do not edit files — answer in a final markdown reply.")
    }

    const val BUILD_PLAN_USER_MESSAGE =
        "Build the plan above. Implement every checklist item using file tools."

    fun isPlanLike(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 80) return false
        return trimmed.contains("- [ ]") || trimmed.contains("- [x]", ignoreCase = true) ||
            trimmed.contains("## ") || trimmed.contains("### ")
    }

    fun isTruncatedProtocolJson(raw: String): Boolean =
        looksLikeProtocolJson(raw) && extractBalancedObject(raw, raw.indexOf('{')) == null

    private fun isParseFailureFinal(text: String): Boolean = text == PARSE_FAILURE_MESSAGE

    private fun parseRootObject(raw: String): JsonObject? {
        val trimmed = raw.trim()
        val candidates = buildList {
            extractJson(trimmed)?.let { add(it) }
            extractBalancedObject(trimmed, trimmed.indexOf('{'))?.let { add(it) }
            if (trimmed.startsWith("{")) add(trimmed)
        }.distinct()
        for (candidate in candidates) {
            parseJsonObject(candidate)?.let { return it }
            parseJsonObject(repairJsonStringEscapes(candidate))?.let { return it }
        }
        return null
    }

    private fun parseJsonObject(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    private fun extractThought(raw: String): String? {
        val root = parseRootObject(raw) ?: return null
        return root["thought"]?.stringOrNull()?.takeIf { it.isNotBlank() }
    }

    /** Pulls individual tool objects out of malformed/truncated agent JSON. */
    internal fun salvageActions(raw: String): List<AgentAction> {
        val actions = LinkedHashMap<String, AgentAction>()
        var index = 0
        while (index < raw.length) {
            val toolIdx = raw.indexOf("\"tool\"", index)
            if (toolIdx < 0) break
            var start = toolIdx
            while (start > 0 && raw[start] != '{') start--
            if (raw.getOrNull(start) != '{') {
                index = toolIdx + 6
                continue
            }
            val objText = extractBalancedObject(raw, start) ?: break
            parseJsonObject(objText)?.toActionOrNull()?.let { action ->
                val key = when (action) {
                    is AgentAction.Search -> "search:${action.query}"
                    is AgentAction.ListDir -> "list_dir:${action.path}"
                    is AgentAction.ReadFile -> "read_file:${action.path}"
                    is AgentAction.CreateFile -> "create_file:${action.path}"
                    is AgentAction.CreateDir -> "create_dir:${action.path}"
                    is AgentAction.EditFile -> "edit_file:${action.path}"
                    is AgentAction.Rename -> "rename:${action.path}:${action.newName}"
                    is AgentAction.Move -> "move:${action.path}:${action.newParent}"
                    is AgentAction.Delete -> "delete:${action.path}"
                }
                actions[key] = action
            }
            index = start + objText.length
        }
        return actions.values.toList()
    }

    /** Escapes raw newlines/tabs that models often emit inside JSON string values. */
    internal fun repairJsonStringEscapes(source: String): String {
        val out = StringBuilder(source.length + 32)
        var inString = false
        var escaped = false
        for (c in source) {
            when {
                escaped -> {
                    out.append(c)
                    escaped = false
                }
                c == '\\' && inString -> {
                    out.append(c)
                    escaped = true
                }
                c == '"' -> {
                    out.append(c)
                    inString = !inString
                }
                inString && c == '\n' -> out.append("\\n")
                inString && c == '\r' -> out.append("\\r")
                inString && c == '\t' -> out.append("\\t")
                else -> out.append(c)
            }
        }
        return out.toString()
    }

    /** @see parseExecutionTurn */
    fun parse(raw: String): AgentTurn = parseExecutionTurn(raw, preferActions = false)

    private fun parseActions(root: JsonObject): List<AgentAction> {
        return when (val actionsEl = root["actions"]) {
            is JsonArray -> actionsEl.mapNotNull { it.toActionOrNull() }
            is JsonObject -> listOfNotNull(actionsEl.toActionOrNull())
            else -> emptyList()
        }
    }

    /** Never surface raw protocol JSON in the chat UI. */
    fun sanitizeDisplayText(raw: String): String {
        val trimmed = raw.trim()
        if (!looksLikeProtocolJson(trimmed)) return trimmed
        extractJson(trimmed)?.let { jsonText ->
            runCatching { json.parseToJsonElement(jsonText).jsonObject }.getOrNull()?.let { root ->
                root["final"]?.stringOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
                root["thought"]?.stringOrNull()?.takeIf { it.isNotBlank() }?.let { return it }
            }
        }
        return PARSE_FAILURE_MESSAGE
    }

    fun looksLikeProtocolJson(text: String): Boolean {
        val t = text.trimStart()
        return t.startsWith("{") && (t.contains("\"actions\"") || t.contains("\"final\"") || t.contains("\"tool\""))
    }

    private fun JsonElement.toActionOrNull(): AgentAction? {
        val obj = this as? JsonObject ?: return null
        val tool = obj["tool"]?.stringOrNull() ?: return null
        fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { obj[it]?.stringOrNull() }
        return when (tool) {
            "list_dir" -> AgentAction.ListDir(str("path", "dir") ?: ".")
            "read_file" -> str("path", "file")?.let { AgentAction.ReadFile(it) }
            "search" -> str("query", "q", "text")?.let { AgentAction.Search(it) }
            "create_file" -> str("path", "file")?.let {
                AgentAction.CreateFile(it, str("content", "text", "file_content", "body") ?: "")
            }
            "create_dir", "create_directory", "mkdir" -> str("path", "dir")?.let { AgentAction.CreateDir(it) }
            "edit_file", "write_file", "update_file" ->
                str("path", "file")?.let { AgentAction.EditFile(it, str("content", "text", "file_content", "body", "new_content") ?: "") }
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
        return extractBalancedObject(candidate, start)
    }

    private fun extractBalancedObject(raw: String, start: Int): String? {
        if (start < 0 || start >= raw.length || raw[start] != '{') return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in start until raw.length) {
            val c = raw[i]
            when {
                escaped -> escaped = false
                c == '\\' && inString -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, i + 1)
                }
            }
        }
        return null
    }
}
