package com.ahmadkharfan.androidstudiolite.data.ai.agent

import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode

sealed interface AgentTurn {
    data class Actions(val thought: String?, val actions: List<AgentAction>) : AgentTurn
    data class Final(val text: String) : AgentTurn
}

object AgentProtocol {

    const val PARSE_FAILURE_MESSAGE = "I couldn't apply the changes. Please try again."

    const val BUILD_PLAN_USER_MESSAGE = AgentPrompts.BUILD_PLAN_USER_MESSAGE

    fun systemPrompt(
        userInstructions: String,
        projectOutline: String,
        activeFilePath: String?,
        mode: ChatMode = ChatMode.AGENT,
        sourcePackagePrefix: String? = null,
        projectLanguage: String = "Unknown",
    ): String = AgentPrompts.systemPrompt(
        userInstructions,
        projectOutline,
        activeFilePath,
        mode,
        sourcePackagePrefix,
        projectLanguage,
    )

    fun parseExecutionTurn(raw: String, preferActions: Boolean = false): AgentTurn =
        AgentReplyParser.parseExecutionTurn(raw, preferActions)

    fun parse(raw: String): AgentTurn = AgentReplyParser.parse(raw)

    fun jsonRetryPrompt(): String = AgentPrompts.jsonRetryPrompt()

    fun implementationContinuePrompt(toolsRun: Int): String =
        AgentPrompts.implementationContinuePrompt(toolsRun)

    fun continueImplementationPrompt(toolsRun: Int): String =
        AgentPrompts.continueImplementationPrompt(toolsRun)

    fun implementPlanPrompt(planMarkdown: String): String =
        AgentPrompts.implementPlanPrompt(planMarkdown)

    fun implementPlanRetryPrompt(userMessage: String): String =
        AgentPrompts.implementPlanRetryPrompt(userMessage)

    fun reviewPlanPrompt(userInstructions: String? = null): String =
        AgentPrompts.reviewPlanPrompt(userInstructions)

    fun isUnparsedProtocolResponse(raw: String, finalText: String): Boolean =
        AgentReplyClassifier.isUnparsedProtocolResponse(raw, finalText)

    fun isThoughtOnlyTurn(raw: String): Boolean =
        AgentReplyClassifier.isThoughtOnlyTurn(raw)

    fun looksLikeActionRequest(text: String): Boolean =
        AgentReplyClassifier.looksLikeActionRequest(text)

    fun looksLikeProtocolJson(text: String): Boolean =
        AgentReplyClassifier.looksLikeProtocolJson(text)

    fun isPlanLike(text: String): Boolean =
        AgentReplyClassifier.isPlanLike(text)

    fun isTruncatedProtocolJson(raw: String): Boolean =
        AgentReplyClassifier.isTruncatedProtocolJson(raw)

    fun sanitizeDisplayText(raw: String): String =
        AgentReplyClassifier.sanitizeDisplayText(raw)

    fun shouldAutoContinueImplementation(mode: ChatMode, toolsRun: Int, userText: String): Boolean =
        AgentReplyClassifier.shouldAutoContinueImplementation(mode, toolsRun, userText)

    fun isImplementPlanMessage(userText: String): Boolean =
        AgentReplyClassifier.isImplementPlanMessage(userText)

    fun diagnoseParse(raw: String, preferActions: Boolean): ParseDiagnostic =
        AgentReplyClassifier.diagnoseParse(raw, preferActions)

    internal fun salvageActions(raw: String): List<AgentAction> =
        AgentReplySalvage.salvageActions(raw)

    internal fun repairJsonStringEscapes(source: String): String =
        AgentReplySalvage.repairJsonStringEscapes(source)

    internal fun extractLooseJsonStringValue(
        raw: String,
        contentStart: Int,
        searchEnd: Int = raw.length,
    ): String? = AgentReplySalvage.extractLooseJsonStringValue(raw, contentStart, searchEnd)

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
}
