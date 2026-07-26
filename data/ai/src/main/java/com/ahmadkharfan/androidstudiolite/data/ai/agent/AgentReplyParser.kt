package com.ahmadkharfan.androidstudiolite.data.ai.agent

import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal object AgentReplyParser {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun parseExecutionTurn(raw: String, preferActions: Boolean = false): AgentTurn {
        val diagnostic = AgentReplyClassifier.diagnoseParse(raw, preferActions)
        AiAgentLog.i("Parse", diagnostic.summary)

        val root = parseRootObject(raw)
        if (root != null) {
            val turn = turnFromRoot(root, preferActions)
            if (turn is AgentTurn.Actions ||
                turn is AgentTurn.Final && !AgentReplyClassifier.isParseFailureFinal(turn.text)
            ) {
                AiAgentLog.i("Parse", "outcome=${diagnostic.outcome} turn=${AgentReplyClassifier.turnSummary(turn)}")
                return turn
            }
            AiAgentLog.w(
                "Parse",
                "root parsed but unusable turn=${AgentReplyClassifier.turnSummary(turn)}; " +
                    "salvaged=${diagnostic.salvagedCount}",
            )
        } else {
            AiAgentLog.w(
                "Parse",
                "root parse failed; looksLikeProtocol=${diagnostic.looksLikeProtocol} " +
                    "truncated=${diagnostic.truncated} salvaged=${diagnostic.salvagedCount}",
            )
        }

        val salvaged = AgentReplySalvage.salvageActions(raw).map { AgentContentSanitizer.sanitizeAction(it) }
        if (preferActions && salvaged.isNotEmpty()) {
            AgentReplyClassifier.logSanitizedActions(salvaged)
            AiAgentLog.i("Parse", "using salvaged actions count=${salvaged.size}")
            return AgentTurn.Actions(AgentReplySalvage.extractThought(raw), salvaged)
        }

        val sanitized = AgentReplyClassifier.sanitizeDisplayText(raw)
        AiAgentLog.w(
            "Parse",
            "fallback sanitize -> " +
                "${if (sanitized == AgentProtocol.PARSE_FAILURE_MESSAGE) "PARSE_FAILURE" else "text(${sanitized.length})"} " +
                "rawPreview=${AiAgentLog.preview(raw)} rawTail=${AiAgentLog.tail(raw)}",
        )
        return AgentTurn.Final(sanitized)
    }

    fun parse(raw: String): AgentTurn = parseExecutionTurn(raw, preferActions = false)

    private fun turnFromRoot(root: JsonObject, preferActions: Boolean): AgentTurn {
        val thought = root["thought"]?.let(::stringOrNull)
        val actions = parseActions(root)
        val final = root["final"]?.let(::stringOrNull)

        if (preferActions && actions.isNotEmpty()) {
            val sanitized = actions.map { AgentContentSanitizer.sanitizeAction(it) }
            AgentReplyClassifier.logSanitizedActions(sanitized)
            return AgentTurn.Actions(thought, sanitized)
        }

        if (preferActions && root.containsKey("actions") && actions.isEmpty() && final.isNullOrBlank()) {
            AiAgentLog.w("Parse", "preferActions=true but actions=[] with no final")
            return AgentTurn.Final(AgentProtocol.PARSE_FAILURE_MESSAGE)
        }

        if (!final.isNullOrBlank()) {
            if (preferActions && actions.isEmpty()) {
                AiAgentLog.w("Parse", "preferActions=true but model sent final-only JSON (${final.length} chars)")
            }
            return AgentTurn.Final(final)
        }

        if (actions.isNotEmpty()) {
            val sanitized = actions.map { AgentContentSanitizer.sanitizeAction(it) }
            AgentReplyClassifier.logSanitizedActions(sanitized)
            return AgentTurn.Actions(thought, sanitized)
        }
        toActionOrNull(root)?.let { action ->
            val sanitized = AgentContentSanitizer.sanitizeAction(action)
            AgentReplyClassifier.logSanitizedActions(listOf(sanitized))
            return AgentTurn.Actions(thought, listOf(sanitized))
        }

        return AgentTurn.Final(thought?.takeIf { it.isNotBlank() } ?: AgentProtocol.PARSE_FAILURE_MESSAGE)
    }

    fun parseRootObject(raw: String): JsonObject? {
        val trimmed = raw.trim()
        val candidates = buildList {
            extractJson(trimmed)?.let { add(it) }
            extractBalancedObject(trimmed, trimmed.indexOf('{'))?.let { add(it) }
            if (trimmed.startsWith("{")) add(trimmed)
        }.distinct()
        for (candidate in candidates) {
            parseJsonObject(candidate)?.let { return it }
            parseJsonObject(AgentReplySalvage.repairJsonStringEscapes(candidate))?.let { return it }
        }
        return null
    }

    fun parseJsonObject(text: String): JsonObject? =
        runCatching { json.parseToJsonElement(text).jsonObject }.getOrNull()

    fun parseActions(root: JsonObject): List<AgentAction> =
        when (val actionsElement = root["actions"]) {
            is JsonArray -> actionsElement.mapNotNull(::toActionOrNull)
            is JsonObject -> listOfNotNull(toActionOrNull(actionsElement))
            else -> emptyList()
        }

    fun toActionOrNull(element: JsonElement): AgentAction? {
        val obj = element as? JsonObject ?: return null
        val tool = obj["tool"]?.let(::stringOrNull) ?: return null
        fun str(vararg keys: String): String? = keys.firstNotNullOfOrNull { obj[it]?.let(::stringOrNull) }
        return when (tool) {
            "list_dir" -> AgentAction.ListDir(str("path", "dir") ?: ".")
            "read_file" -> str("path", "file")?.let { AgentAction.ReadFile(it) }
            "search" -> str("query", "q", "text")?.let { AgentAction.Search(it) }
            "create_file" -> str("path", "file")?.let {
                AgentAction.CreateFile(it, str("content", "text", "file_content", "body") ?: "")
            }
            "create_dir", "create_directory", "mkdir" -> str("path", "dir")?.let { AgentAction.CreateDir(it) }
            "edit_file", "write_file", "update_file" ->
                str("path", "file")?.let {
                    AgentAction.EditFile(it, str("content", "text", "file_content", "body", "new_content") ?: "")
                }
            "rename" -> str("path", "file")?.let { path ->
                str("new_name", "newName", "name")?.let { AgentAction.Rename(path, it) }
            }
            "move" -> str("path", "file")?.let { path ->
                str("new_parent", "newParent", "dest", "destination", "to")?.let { AgentAction.Move(path, it) }
            }
            "delete", "remove" -> str("path", "file")?.let { AgentAction.Delete(it) }
            else -> null
        }
    }

    fun stringOrNull(element: JsonElement): String? =
        runCatching { element.jsonPrimitive.contentOrNull }.getOrNull()

    fun putAction(actions: LinkedHashMap<String, AgentAction>, action: AgentAction) {
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

    fun extractJson(raw: String): String? {
        val fenced = Regex("```(?:json)?\\s*([\\s\\S]*?)```").find(raw)?.groupValues?.get(1)?.trim()
        val candidate = fenced ?: raw
        val start = candidate.indexOf('{')
        if (start < 0) return null
        return extractBalancedObject(candidate, start)
    }

    fun extractBalancedObject(raw: String, start: Int): String? {
        if (start < 0 || start >= raw.length || raw[start] != '{') return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until raw.length) {
            val character = raw[index]
            when {
                escaped -> escaped = false
                character == '\\' && inString -> escaped = true
                character == '"' -> inString = !inString
                !inString && character == '{' -> depth++
                !inString && character == '}' -> {
                    depth--
                    if (depth == 0) return raw.substring(start, index + 1)
                }
            }
        }
        return null
    }
}
