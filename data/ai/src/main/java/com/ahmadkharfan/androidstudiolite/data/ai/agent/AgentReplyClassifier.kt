package com.ahmadkharfan.androidstudiolite.data.ai.agent

import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode

internal object AgentReplyClassifier {

    fun diagnoseParse(raw: String, preferActions: Boolean): AgentProtocol.ParseDiagnostic {
        val trimmed = raw.trim()
        val root = AgentReplyParser.parseRootObject(raw)
        val actions = root?.let(AgentReplyParser::parseActions).orEmpty()
        val salvaged = AgentReplySalvage.salvageActions(raw)
        val final = root?.get("final")?.let(AgentReplyParser::stringOrNull)
        val thought = root?.get("thought")?.let(AgentReplyParser::stringOrNull)
        val truncated = isTruncatedProtocolJson(raw)
        val looksLike = looksLikeProtocolJson(trimmed)
        val outcome = when {
            root != null && preferActions && actions.isNotEmpty() -> "actions"
            root != null && !final.isNullOrBlank() -> "final"
            root != null && actions.isNotEmpty() -> "actions_non_prefer"
            salvaged.isNotEmpty() && preferActions -> "salvage"
            truncated -> "truncated"
            looksLike && root == null -> "invalid_protocol_json"
            looksLike && salvaged.isEmpty() -> "empty_protocol_json"
            else -> "prose_or_failure"
        }
        return AgentProtocol.ParseDiagnostic(
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

    fun isUnparsedProtocolResponse(raw: String, finalText: String): Boolean {
        if (finalText == AgentProtocol.PARSE_FAILURE_MESSAGE) return true
        if (isTruncatedProtocolJson(raw)) return true
        return looksLikeProtocolJson(raw) &&
            AgentReplyParser.parseRootObject(raw) == null &&
            AgentReplySalvage.salvageActions(raw).isEmpty()
    }

    fun isThoughtOnlyTurn(raw: String): Boolean {
        val root = AgentReplyParser.parseRootObject(raw) ?: return false
        if (AgentReplyParser.parseActions(root).isNotEmpty()) return false
        if (AgentReplySalvage.salvageActions(raw).isNotEmpty()) return false
        if (!root["final"]?.let(AgentReplyParser::stringOrNull).isNullOrBlank()) return false
        return !root["thought"]?.let(AgentReplyParser::stringOrNull).isNullOrBlank()
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

    fun looksLikeProtocolJson(text: String): Boolean {
        val trimmed = text.trimStart()
        return trimmed.startsWith("{") &&
            (trimmed.contains("\"actions\"") || trimmed.contains("\"final\"") || trimmed.contains("\"tool\""))
    }

    fun isPlanLike(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 80) return false
        return trimmed.contains("- [ ]") || trimmed.contains("- [x]", ignoreCase = true) ||
            trimmed.contains("## ") || trimmed.contains("### ")
    }

    fun isTruncatedProtocolJson(raw: String): Boolean =
        looksLikeProtocolJson(raw) && AgentReplyParser.extractBalancedObject(raw, raw.indexOf('{')) == null

    fun sanitizeDisplayText(raw: String): String {
        val trimmed = raw.trim()
        if (!looksLikeProtocolJson(trimmed)) return trimmed
        AgentReplyParser.extractJson(trimmed)?.let(AgentReplyParser::parseJsonObject)?.let { root ->
            root["final"]?.let(AgentReplyParser::stringOrNull)?.takeIf { it.isNotBlank() }?.let { return it }
            root["thought"]?.let(AgentReplyParser::stringOrNull)?.takeIf { it.isNotBlank() }?.let { return it }
        }
        return AgentProtocol.PARSE_FAILURE_MESSAGE
    }

    fun shouldAutoContinueImplementation(mode: ChatMode, toolsRun: Int, userText: String): Boolean =
        mode == ChatMode.AGENT &&
            (toolsRun > 0 || looksLikeActionRequest(userText) || isImplementPlanMessage(userText))

    fun isImplementPlanMessage(userText: String): Boolean =
        userText.startsWith("Implement the plan below step by step")

    fun turnSummary(turn: AgentTurn): String = when (turn) {
        is AgentTurn.Actions -> "Actions(count=${turn.actions.size}, thought=${turn.thought?.length ?: 0})"
        is AgentTurn.Final ->
            "Final(len=${turn.text.length}, parseFailure=${turn.text == AgentProtocol.PARSE_FAILURE_MESSAGE})"
    }

    fun logSanitizedActions(actions: List<AgentAction>) {
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

    fun isParseFailureFinal(text: String): Boolean =
        text == AgentProtocol.PARSE_FAILURE_MESSAGE
}
