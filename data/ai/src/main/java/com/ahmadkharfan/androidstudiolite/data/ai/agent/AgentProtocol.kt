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

sealed interface AgentTurn {
    data class Actions(val thought: String?, val actions: List<AgentAction>) : AgentTurn
    data class Final(val text: String) : AgentTurn
}

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
                    appendLine("- In JSON file content strings, escape every double quote as \\\" and every newline as \\n.")
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

    fun parseExecutionTurn(raw: String, preferActions: Boolean = false): AgentTurn {
        val diagnostic = diagnoseParse(raw, preferActions)
        AiAgentLog.i("Parse", diagnostic.summary)

        val root = parseRootObject(raw)
        if (root != null) {
            val turn = turnFromRoot(root, preferActions)
            if (turn is AgentTurn.Actions || turn is AgentTurn.Final && !isParseFailureFinal(turn.text)) {
                AiAgentLog.i("Parse", "outcome=${diagnostic.outcome} turn=${turnSummary(turn)}")
                return turn
            }
            AiAgentLog.w(
                "Parse",
                "root parsed but unusable turn=${turnSummary(turn)}; salvaged=${diagnostic.salvagedCount}",
            )
        } else {
            AiAgentLog.w(
                "Parse",
                "root parse failed; looksLikeProtocol=${diagnostic.looksLikeProtocol} " +
                    "truncated=${diagnostic.truncated} salvaged=${diagnostic.salvagedCount}",
            )
        }

        val salvaged = salvageActions(raw).map { AgentContentSanitizer.sanitizeAction(it) }
        if (preferActions && salvaged.isNotEmpty()) {
            logSanitizedActions(salvaged)
            AiAgentLog.i("Parse", "using salvaged actions count=${salvaged.size}")
            return AgentTurn.Actions(extractThought(raw), salvaged)
        }

        val sanitized = sanitizeDisplayText(raw)
        AiAgentLog.w(
            "Parse",
            "fallback sanitize -> ${if (sanitized == PARSE_FAILURE_MESSAGE) "PARSE_FAILURE" else "text(${sanitized.length})"} " +
                "rawPreview=${AiAgentLog.preview(raw)} rawTail=${AiAgentLog.tail(raw)}",
        )
        return AgentTurn.Final(sanitized)
    }

    fun diagnoseParse(raw: String, preferActions: Boolean): ParseDiagnostic {
        val trimmed = raw.trim()
        val root = parseRootObject(raw)
        val actions = root?.let { parseActions(it) }.orEmpty()
        val salvaged = salvageActions(raw)
        val final = root?.get("final")?.stringOrNull()
        val thought = root?.get("thought")?.stringOrNull()
        val truncated = isTruncatedProtocolJson(raw)
        val looksLike = looksLikeProtocolJson(trimmed)
        val outcome = when {
            root != null && preferActions && actions.isNotEmpty() -> "actions"
            root != null && !final.isNullOrBlank() -> "final"
            root != null && actions.isNotEmpty() -> "actions_non_prefer"
            salvaged.isNotEmpty() && preferActions -> "salvage"
            truncated -> "truncated"
            looksLike && root == null -> "invalid_protocol_json"
            looksLike && actions.isEmpty() && salvaged.isEmpty() -> "empty_protocol_json"
            else -> "prose_or_failure"
        }
        return ParseDiagnostic(
            rawLength = trimmed.length,
            looksLikeProtocol = looksLike,
            rootParsed = root != null,
            actionCount = actions.size,
            salvagedCount = salvaged.size,
            hasFinal = !final.isNullOrBlank(),
            hasThought = !thought.isNullOrBlank(),
            truncated = truncated,
            preferActions = preferActions,
            outcome = outcome,
        )
    }

    data class ParseDiagnostic(
        val rawLength: Int,
        val looksLikeProtocol: Boolean,
        val rootParsed: Boolean,
        val actionCount: Int,
        val salvagedCount: Int,
        val hasFinal: Boolean,
        val hasThought: Boolean,
        val truncated: Boolean,
        val preferActions: Boolean,
        val outcome: String,
    ) {
        val summary: String
            get() = buildString {
                append("len=$rawLength preferActions=$preferActions outcome=$outcome ")
                append("root=$rootParsed actions=$actionCount salvaged=$salvagedCount ")
                append("final=$hasFinal thought=$hasThought protocol=$looksLikeProtocol truncated=$truncated")
            }
    }

    private fun turnSummary(turn: AgentTurn): String = when (turn) {
        is AgentTurn.Actions -> "Actions(count=${turn.actions.size}, thought=${turn.thought?.length ?: 0})"
        is AgentTurn.Final -> "Final(len=${turn.text.length}, parseFailure=${turn.text == PARSE_FAILURE_MESSAGE})"
    }

    private fun turnFromRoot(root: JsonObject, preferActions: Boolean): AgentTurn {
        val thought = root["thought"]?.stringOrNull()
        val actions = parseActions(root)
        val final = root["final"]?.stringOrNull()

        if (preferActions && actions.isNotEmpty()) {
            val sanitized = actions.map { AgentContentSanitizer.sanitizeAction(it) }
            logSanitizedActions(sanitized)
            return AgentTurn.Actions(thought, sanitized)
        }


        if (preferActions && root.containsKey("actions") && actions.isEmpty() && final.isNullOrBlank()) {
            AiAgentLog.w("Parse", "preferActions=true but actions=[] with no final")
            return AgentTurn.Final(PARSE_FAILURE_MESSAGE)
        }

        if (!final.isNullOrBlank()) {
            if (preferActions && actions.isEmpty()) {
                AiAgentLog.w("Parse", "preferActions=true but model sent final-only JSON (${final.length} chars)")
            }
            return AgentTurn.Final(final)
        }

        if (actions.isNotEmpty()) {
            val sanitized = actions.map { AgentContentSanitizer.sanitizeAction(it) }
            logSanitizedActions(sanitized)
            return AgentTurn.Actions(thought, sanitized)
        }
        root.toActionOrNull()?.let { action ->
            val sanitized = AgentContentSanitizer.sanitizeAction(action)
            logSanitizedActions(listOf(sanitized))
            return AgentTurn.Actions(thought, listOf(sanitized))
        }

        return AgentTurn.Final(thought?.takeIf { it.isNotBlank() } ?: PARSE_FAILURE_MESSAGE)
    }

    fun isUnparsedProtocolResponse(raw: String, finalText: String): Boolean {
        if (finalText == PARSE_FAILURE_MESSAGE) return true
        if (isTruncatedProtocolJson(raw)) return true
        return looksLikeProtocolJson(raw) &&
            parseRootObject(raw) == null &&
            salvageActions(raw).isEmpty()
    }

    fun isThoughtOnlyTurn(raw: String): Boolean {
        val root = parseRootObject(raw) ?: return false
        if (parseActions(root).isNotEmpty()) return false
        if (salvageActions(raw).isNotEmpty()) return false
        if (!root["final"]?.stringOrNull().isNullOrBlank()) return false
        return !root["thought"]?.stringOrNull().isNullOrBlank()
    }

    fun shouldAutoContinueImplementation(mode: ChatMode, toolsRun: Int, userText: String): Boolean =
        mode == ChatMode.AGENT && (toolsRun > 0 || looksLikeActionRequest(userText) || isImplementPlanMessage(userText))

    fun isImplementPlanMessage(userText: String): Boolean =
        userText.startsWith("Implement the plan below step by step")

    fun continueImplementationPrompt(toolsRun: Int): String =
        if (toolsRun > 0) {
            "Continue implementing the remaining plan steps. You already ran $toolsRun tool(s). " +
                "Reply with ONE JSON object containing file tool actions — do not send thought-only JSON. " +
                "Use edit_file/create_file with escaped \\\" and \\n inside content."
        } else {
            "Continue implementing the plan. Reply with ONE JSON object containing file tool actions only."
        }

    private fun logSanitizedActions(actions: List<AgentAction>) {
        actions.forEach { action ->
            when (action) {
                is AgentAction.CreateFile -> {
                    val warnings = AgentContentSanitizer.validateFileContent(action.path, action.content)
                    if (warnings.isNotEmpty()) {
                        AiAgentLog.w("Content", "create_file ${action.path}: ${warnings.joinToString()}")
                    }
                }
                is AgentAction.EditFile -> {
                    val warnings = AgentContentSanitizer.validateFileContent(action.path, action.content)
                    if (warnings.isNotEmpty()) {
                        AiAgentLog.w("Content", "edit_file ${action.path}: ${warnings.joinToString()}")
                    }
                }
                else -> Unit
            }
        }
    }

    fun looksLikeActionRequest(text: String): Boolean {
        val lower = text.lowercase()
        val keywords = listOf(
            "implement", "apply", "do it", "build", "create", "fix", "add", "update",
            "edit", "refactor", "make", "write", "change", "go ahead", "proceed",
            "execute", "start", "do this", "please", "now", "continue",
        )
        return keywords.any { it in lower }
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

    fun implementPlanRetryPrompt(userMessage: String): String =
        if (userMessage.startsWith("Implement the plan below")) {
            "Your last reply was not valid agent JSON, so no file edits ran. Reply with ONE JSON object " +
                "only — use {\"thought\":\"...\",\"actions\":[{\"tool\":\"edit_file\",\"path\":\"...\",\"content\":\"...\"}]} " +
                "to implement the plan above. Escape newlines as \\n inside file content. No markdown fences."
        } else {
            jsonRetryPrompt()
        }

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
            val action = extractBalancedObject(raw, start)?.let { parseJsonObject(it)?.toActionOrNull() }
                ?: salvageLooseFileAction(raw, start)
            action?.let { putAction(actions, it) }
            index = toolIdx + 6
        }
        return actions.values.toList()
    }

    internal fun salvageLooseFileAction(raw: String, objectStart: Int): AgentAction? {
        if (objectStart < 0 || objectStart >= raw.length) return null
        val slice = raw.substring(objectStart)
        val toolMatch = LOOSE_FILE_TOOL.find(slice) ?: return null
        val tool = toolMatch.groupValues[1]
        val absoluteToolIdx = objectStart + toolMatch.range.first
        val pathMatch = LOOSE_PATH.find(slice, toolMatch.range.last + 1) ?: return null
        val path = unescapeJsonString(pathMatch.groupValues[1])
        val contentKeyIdx = slice.indexOf("\"content\"", pathMatch.range.last)
        if (contentKeyIdx < 0) return null
        val openQuoteIdx = slice.indexOf('"', slice.indexOf(':', contentKeyIdx) + 1)
        if (openQuoteIdx < 0) return null
        val contentStart = objectStart + openQuoteIdx + 1
        val searchEnd = looseContentSearchEnd(raw, absoluteToolIdx)
        val content = extractLooseJsonStringValue(raw, contentStart, searchEnd)
            ?.let { AgentContentSanitizer.sanitizeFileContent(path, it) }
            ?: return null
        AiAgentLog.i("Parse", "salvageLooseFileAction tool=$tool path=$path contentLen=${content.length}")
        return when (tool) {
            "create_file" -> AgentAction.CreateFile(path, content)
            "edit_file" -> AgentAction.EditFile(path, content)
            else -> null
        }
    }

    private fun putAction(actions: LinkedHashMap<String, AgentAction>, action: AgentAction) {
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

    private val LOOSE_FILE_TOOL = Regex(""""tool"\s*:\s*"(create_file|edit_file)"""")
    private val LOOSE_PATH = Regex(""""path"\s*:\s*"((?:\\.|[^"\\])*)"""")

    internal fun extractLooseJsonStringValue(
        raw: String,
        contentStart: Int,
        searchEnd: Int = raw.length,
    ): String? {
        if (contentStart < 0 || contentStart >= raw.length) return null
        val endBound = searchEnd.coerceIn(contentStart, raw.length)
        val slice = raw.substring(contentStart, endBound)
        val endPatterns = listOf("\"},", "\"}]", "\"}}]", "\"}]}", "\"}}}", "\"}}", "\"}")
        var end = -1
        for (pattern in endPatterns) {
            val idx = slice.indexOf(pattern)
            if (idx >= 0 && (end < 0 || idx < end)) {
                end = idx
            }
        }
        if (end < 0) return null
        return unescapeJsonString(slice.substring(0, end))
    }

    private fun looseContentSearchEnd(raw: String, toolKeyIndex: Int): Int {
        val nextTool = raw.indexOf("\"tool\"", toolKeyIndex + 6)
        return if (nextTool > toolKeyIndex) nextTool else raw.length
    }

    internal fun unescapeJsonString(source: String): String = buildString(source.length) {
        var i = 0
        while (i < source.length) {
            if (source[i] == '\\' && i + 1 < source.length) {
                when (source[i + 1]) {
                    'n' -> { append('\n'); i += 2 }
                    'r' -> { append('\r'); i += 2 }
                    't' -> { append('\t'); i += 2 }
                    '"' -> { append('"'); i += 2 }
                    '\\' -> { append('\\'); i += 2 }
                    else -> { append(source[i]); i++ }
                }
            } else {
                append(source[i])
                i++
            }
        }
    }

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

    fun parse(raw: String): AgentTurn = parseExecutionTurn(raw, preferActions = false)

    private fun parseActions(root: JsonObject): List<AgentAction> {
        return when (val actionsEl = root["actions"]) {
            is JsonArray -> actionsEl.mapNotNull { it.toActionOrNull() }
            is JsonObject -> listOfNotNull(actionsEl.toActionOrNull())
            else -> emptyList()
        }
    }

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
