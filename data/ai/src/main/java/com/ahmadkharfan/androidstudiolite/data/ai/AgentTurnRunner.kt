package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.data.ai.agent.AgentProtocol
import com.ahmadkharfan.androidstudiolite.data.ai.agent.AgentTools
import com.ahmadkharfan.androidstudiolite.data.ai.agent.AgentTurn
import com.ahmadkharfan.androidstudiolite.data.ai.agent.AiAgentLog
import com.ahmadkharfan.androidstudiolite.data.ai.agent.StreamingJsonFieldExtractor
import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction
import com.ahmadkharfan.androidstudiolite.domain.model.AgentToolResult
import com.ahmadkharfan.androidstudiolite.domain.model.AiAgentSettings
import com.ahmadkharfan.androidstudiolite.domain.model.AiProviderConfig
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMessageKind
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import com.ahmadkharfan.androidstudiolite.domain.model.ChatToolCall
import com.ahmadkharfan.androidstudiolite.domain.model.ToolCallStatus
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val AgentTurn.Final.isParseFailure: Boolean
    get() = text == AgentProtocol.PARSE_FAILURE_MESSAGE

internal data class LlmStreamRequest(
    val providerId: String,
    val apiKey: String,
    val model: String,
    val systemPrompt: String,
    val turns: List<LlmChatTurn>,
    val baseUrl: String?,
)

internal fun interface LlmStreamer {
    fun stream(request: LlmStreamRequest, onDelta: (String) -> Unit): String
}

internal data class AgentRequest(
    val session: ChatSession,
    val provider: AiProviderConfig,
    val apiKey: String,
    val model: String,
    val mode: ChatMode,
    val settings: AiAgentSettings,
    val activeFilePath: String?,
    val userText: String,
)

internal class AgentTurnRunner(
    private val streamer: LlmStreamer,
    private val tools: AgentTools,
    private val messages: ChatMessageEditor,
    private val pendingApprovals: ConcurrentHashMap<String, CompletableDeferred<Boolean>>,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    suspend fun run(request: AgentRequest) = AgentLoop(request).run()

    private inner class AgentLoop(request: AgentRequest) {
        private val session = request.session
        private val provider = request.provider
        private val apiKey = request.apiKey
        private val model = request.model
        private val mode = request.mode
        private val settings = request.settings
        private val activeFilePath = request.activeFilePath
        private val userText = request.userText
        private val agent = mode == ChatMode.AGENT
        private val state = LoopState()
        private val turns = initialTurns()

        suspend fun run() {
            val system = systemPrompt()
            AiAgentLog.i(
                "Loop",
                "start mode=$mode provider=${provider.id} model=$model turns=${turns.size} " +
                    "userPreview=${AiAgentLog.preview(userText, 120)}",
            )
            while (state.iteration < MAX_ITERATIONS) {
                if (runIteration(system)) return
            }
            messages.appendAiMessage(session, "Stopped after $MAX_ITERATIONS steps to avoid running away. Ask me to continue if needed.")
        }

        private suspend fun runIteration(system: String): Boolean {
            state.iteration++
            AiAgentLog.d(
                "Loop",
                "iteration=${state.iteration} toolsRun=${state.toolsRun} " +
                    "parseRetries=${state.parseRetryCount} actionNudged=${state.actionNudged}",
            )
            val streamResult = streamOneTurn(system)
            if (streamResult == null) {
                AiAgentLog.w("Loop", "streamOneTurn returned null (hard failure)")
                return true
            }
            val (raw, thoughtId, answerId) = streamResult
            turns.add(LlmChatTurn(ChatRole.AI, raw))
            return when (val turn = AgentProtocol.parseExecutionTurn(raw, preferActions = agent)) {
                is AgentTurn.Final -> !handleFinalTurn(raw, turn, thoughtId, answerId)
                is AgentTurn.Actions -> {
                    handleActionsTurn(turn, thoughtId, answerId)
                    false
                }
            }
        }

        private suspend fun systemPrompt(): String = AgentProtocol.systemPrompt(
            settings.instructions,
            tools.outline(session.projectId),
            activeFilePath,
            mode,
            tools.sourcePackagePrefix(session.projectId),
            tools.projectLanguage(session.projectId),
        )

        private fun initialTurns(): MutableList<LlmChatTurn> {
            val historyMessages = session.activeMessages().filter {
                it.id != WELCOME_ID && it.toolCall == null && it.kind != ChatMessageKind.THINKING && it.text.isNotBlank()
            }
            val turns = coalesceTurns(historyMessages.map { LlmChatTurn(it.role, it.text) }).toMutableList()
            if (mode == ChatMode.AGENT && userText == AgentProtocol.BUILD_PLAN_USER_MESSAGE) {
                findPlanMessage(historyMessages)?.let { plan ->
                    turns.add(LlmChatTurn(ChatRole.USER, AgentProtocol.implementPlanPrompt(plan.text)))
                }
            }
            return turns
        }

        private suspend fun streamOneTurn(system: String): Triple<String, String, String?>? {
            val thoughtId = messages.newMessageId()
            messages.appendStreamingMessage(session, thoughtId, ChatMessageKind.THINKING, "")
            val accumulator = StreamAccumulator(session, thoughtId)
            val extractor = StreamingJsonFieldExtractor(
                onThought = accumulator::appendThought,
                onFinal = accumulator::appendFinal,
            )
            val request = LlmStreamRequest(
                provider.id, apiKey, model, system, turns, provider.baseUrl.takeIf { it.isNotBlank() },
            )
            val raw = runCatching {
                withContext(io) { streamer.stream(request) { delta -> extractor.feed(delta) } }
            }.getOrElse { error ->
                AiAgentLog.w("Stream", "chatRawStream failed: ${error.message}", error)
                messages.removeMessage(session, thoughtId)
                accumulator.answerId?.let { messages.removeMessage(session, it) }
                val detail = (error as? AiLlmException)?.message ?: error.message ?: "Unknown error"
                messages.appendAiMessage(session, "Request failed: $detail")
                return null
            }
            accumulator.flush(force = true)
            AiAgentLog.i(
                "Stream",
                "complete rawLen=${raw.length} thoughtLen=${accumulator.thought.length} finalLen=${accumulator.finalText.length} " +
                    "preview=${AiAgentLog.preview(raw)} tail=${AiAgentLog.tail(raw)}",
            )
            return Triple(raw, thoughtId, accumulator.answerId)
        }

        private val canActionNudge
            get() = agent && state.toolsRun == 0 && !state.actionNudged &&
                AgentProtocol.looksLikeActionRequest(userText)

        private fun handleFinalTurn(raw: String, turn: AgentTurn.Final, thoughtId: String, answerId: String?): Boolean {
            AiAgentLog.i(
                "Turn",
                "Final iteration=${state.iteration} parseFailure=${turn.isParseFailure} " +
                    "textLen=${turn.text.length} unparsed=${AgentProtocol.isUnparsedProtocolResponse(raw, turn.text)}",
            )
            tryJsonRetry(raw, turn, thoughtId, answerId)?.let { return it }
            tryParseFailureNudge(turn, thoughtId, answerId)?.let { return it }
            renderPartialFailure(turn, thoughtId, answerId)?.let { return it }
            tryFinalOnlyNudge(raw, turn, thoughtId, answerId)?.let { return it }
            tryProseNudge(raw, turn, thoughtId, answerId)?.let { return it }
            tryThoughtOnlyContinue(raw, thoughtId, answerId)?.let { return it }
            renderFinal(raw, turn, thoughtId, answerId)
            return false
        }

        private fun tryJsonRetry(raw: String, turn: AgentTurn.Final, thoughtId: String, answerId: String?): Boolean? {
            if (!agent || !AgentProtocol.isUnparsedProtocolResponse(raw, turn.text) || state.parseRetryCount >= MAX_PARSE_RETRIES) return null
            state.parseRetryCount++
            AiAgentLog.w("Loop", "json retry ${state.parseRetryCount}/$MAX_PARSE_RETRIES toolsRun=${state.toolsRun}")
            val prompt = if (state.toolsRun > 0) {
                AgentProtocol.implementationContinuePrompt(state.toolsRun)
            } else {
                AgentProtocol.jsonRetryPrompt()
            }
            return retryWith(thoughtId, answerId, prompt)
        }

        private fun tryParseFailureNudge(turn: AgentTurn.Final, thoughtId: String, answerId: String?): Boolean? {
            if (!canActionNudge || !turn.isParseFailure) return null
            state.actionNudged = true
            AiAgentLog.w("Loop", "parse failure -> implementPlanRetryPrompt")
            return retryWith(thoughtId, answerId, AgentProtocol.implementPlanRetryPrompt(userText))
        }

        private fun renderPartialFailure(turn: AgentTurn.Final, thoughtId: String, answerId: String?): Boolean? {
            if (!agent || !turn.isParseFailure || state.toolsRun == 0) return null
            reconcileFinalTurn(thoughtId, answerId, "")
            messages.appendAiMessage(session, appliedButUnparsedMessage(state.toolsRun))
            return false
        }

        private fun tryFinalOnlyNudge(raw: String, turn: AgentTurn.Final, thoughtId: String, answerId: String?): Boolean? {
            val diagnostic = AgentProtocol.diagnoseParse(raw, preferActions = true)
            val finalOnly = diagnostic.rootParsed && diagnostic.actionCount == 0 &&
                diagnostic.salvagedCount == 0 && diagnostic.hasFinal
            if (!canActionNudge || turn.isParseFailure || !finalOnly) return null
            state.actionNudged = true
            AiAgentLog.w("Loop", "final-only JSON without tools -> agent nudge")
            return retryWith(thoughtId, answerId, FINAL_ONLY_NUDGE)
        }

        private fun tryProseNudge(raw: String, turn: AgentTurn.Final, thoughtId: String, answerId: String?): Boolean? {
            if (!canActionNudge || turn.isParseFailure || AgentProtocol.looksLikeProtocolJson(raw) || turn.text.isBlank()) return null
            state.actionNudged = true
            AiAgentLog.w("Loop", "prose without tools -> agent nudge")
            return retryWith(thoughtId, answerId, PROSE_NUDGE)
        }

        private fun tryThoughtOnlyContinue(raw: String, thoughtId: String, answerId: String?): Boolean? {
            if (!agent || !AgentProtocol.isThoughtOnlyTurn(raw) ||
                !AgentProtocol.shouldAutoContinueImplementation(mode, state.toolsRun, userText) ||
                state.thoughtOnlyContinues >= MAX_THOUGHT_ONLY_CONTINUES
            ) {
                return null
            }
            state.thoughtOnlyContinues++
            AiAgentLog.w(
                "Loop",
                "thought-only turn -> auto-continue " +
                    "${state.thoughtOnlyContinues}/$MAX_THOUGHT_ONLY_CONTINUES toolsRun=${state.toolsRun}",
            )
            return retryWith(thoughtId, answerId, AgentProtocol.continueImplementationPrompt(state.toolsRun))
        }

        private fun renderFinal(raw: String, turn: AgentTurn.Final, thoughtId: String, answerId: String?) {
            if (turn.isParseFailure) {
                AiAgentLog.e(
                    "Loop",
                    "showing PARSE_FAILURE to user iteration=${state.iteration} toolsRun=${state.toolsRun} " +
                        "parseRetries=${state.parseRetryCount} actionNudged=${state.actionNudged} " +
                        "diagnostic=${AgentProtocol.diagnoseParse(raw, agent).summary}",
                )
            }
            reconcileFinalTurn(thoughtId, answerId, turn.text)
        }

        private suspend fun handleActionsTurn(turn: AgentTurn.Actions, thoughtId: String, answerId: String?) {
            AiAgentLog.i(
                "Turn",
                "Actions iteration=${state.iteration} count=${turn.actions.size} tools=${turn.actions.map { it.tool }}",
            )
            reconcileActionsTurn(thoughtId, answerId, turn.thought)
            val results = turn.actions.map { runAction(it) }
            state.toolsRun += turn.actions.size
            turns.add(LlmChatTurn(ChatRole.USER, formatToolResults(results, MAX_MODEL_OUTPUT)))
        }

        private fun retryWith(thoughtId: String, answerId: String?, prompt: String): Boolean {
            messages.removeMessage(session, thoughtId)
            answerId?.let { messages.removeMessage(session, it) }
            turns.add(LlmChatTurn(ChatRole.USER, prompt))
            return true
        }

        private fun reconcileFinalTurn(thoughtId: String, answerId: String?, finalText: String) {
            val display = finalText.trim()
            if (AgentProtocol.looksLikeProtocolJson(display)) {
                messages.removeMessage(session, thoughtId)
                answerId?.let { messages.removeMessage(session, it) }
                if (display != AgentProtocol.PARSE_FAILURE_MESSAGE) {
                    messages.appendAiMessage(session, AgentProtocol.sanitizeDisplayText(display))
                }
                return
            }
            messages.finalizeThinking(session, thoughtId, keep = true)
            if (answerId != null) messages.removeMessage(session, answerId)
            if (display.isNotBlank()) {
                if (AgentProtocol.isPlanLike(display) && !agent) {
                    messages.appendPlanMessage(session, display)
                } else {
                    messages.appendAiMessage(session, display)
                }
            }
        }

        private fun reconcileActionsTurn(thoughtId: String, answerId: String?, thought: String?) {
            answerId?.let { messages.removeMessage(session, it) }
            val text = thought?.trim().orEmpty()
            if (text.isBlank() || AgentProtocol.looksLikeProtocolJson(text)) {
                messages.removeMessage(session, thoughtId)
            } else {
                messages.updateMessageText(session, thoughtId, text, streaming = false)
            }
        }

        private suspend fun runAction(action: AgentAction): AgentToolResult {
            val root = withContext(io) { tools.projectRoot(session.projectId) }
            val normalized = tools.normalizeAction(root, action)
            if (!agent && normalized.mutating) return blockReadOnly(normalized)

            val toolCallId = "tool-${UUID.randomUUID()}"
            announceTool(toolCallId, normalized)
            if (normalized.mutating && !settings.autoApply && !awaitApproval(toolCallId)) {
                return AgentToolResult(normalized, ok = false, output = "User rejected this change.")
            }
            val result = tools.run(session.projectId, normalized)
            messages.updateToolCall(
                session,
                toolCallId,
                if (result.ok) ToolCallStatus.DONE else ToolCallStatus.FAILED,
                result.output.take(MAX_CARD_OUTPUT),
            )
            return result
        }

        private suspend fun announceTool(toolCallId: String, normalized: AgentAction) {
            val (diffOld, diffNew) = buildDiff(normalized)
            val status = if (!normalized.mutating || settings.autoApply) ToolCallStatus.RUNNING else ToolCallStatus.PENDING
            messages.appendToolMessage(
                session,
                ChatToolCall(
                    id = toolCallId,
                    tool = normalized.tool,
                    path = normalized.pathOrNull(),
                    summary = normalized.summary(),
                    diffOld = diffOld,
                    diffNew = diffNew,
                    status = status,
                    mutating = normalized.mutating,
                ),
            )
        }

        private fun blockReadOnly(normalized: AgentAction): AgentToolResult {
            val readOnly = "${mode.name.lowercase()} mode is read-only"
            messages.appendToolMessage(
                session,
                ChatToolCall(
                    id = "tool-${UUID.randomUUID()}",
                    tool = normalized.tool,
                    path = normalized.pathOrNull(),
                    summary = normalized.summary(),
                    status = ToolCallStatus.REJECTED,
                    resultText = "Blocked: $readOnly.",
                    mutating = true,
                ),
            )
            return AgentToolResult(normalized, ok = false, output = "Blocked: $readOnly; do not edit files.")
        }

        private suspend fun awaitApproval(toolCallId: String): Boolean {
            val deferred = CompletableDeferred<Boolean>()
            pendingApprovals[toolCallId] = deferred
            val approved = deferred.await()
            pendingApprovals.remove(toolCallId)
            return if (approved) {
                messages.updateToolCall(session, toolCallId, ToolCallStatus.RUNNING, null)
                true
            } else {
                messages.updateToolCall(session, toolCallId, ToolCallStatus.REJECTED, "Rejected by user")
                false
            }
        }

        private suspend fun buildDiff(action: AgentAction): Pair<String?, String?> = when (action) {
            is AgentAction.CreateFile -> null to action.content
            is AgentAction.EditFile -> tools.readTextOrNull(session.projectId, action.path) to action.content
            else -> null to null
        }

        private fun appliedButUnparsedMessage(toolsRun: Int) =
            "Applied **$toolsRun** change(s). Some steps could not be parsed. " +
                "Tap **Continue** or say \"continue implementation\" to finish the plan."
    }

    private inner class StreamAccumulator(private val session: ChatSession, private val thoughtId: String) {
        val thought = StringBuilder()
        val finalText = StringBuilder()
        var answerId: String? = null
            private set
        private var lastFlush = 0L

        fun appendThought(chunk: String) {
            thought.append(chunk)
            flush()
        }

        fun appendFinal(chunk: String) {
            finalText.append(chunk)
            flush()
        }

        fun flush(force: Boolean = false) {
            val now = System.currentTimeMillis()
            if (!force && now - lastFlush < STREAM_FLUSH_MS) return
            lastFlush = now
            messages.updateMessageText(session, thoughtId, thought.toString(), streaming = true)
            if (finalText.isNotEmpty()) {
                val id = answerId ?: messages.newMessageId().also { newId ->
                    answerId = newId
                    messages.appendStreamingMessage(session, newId, ChatMessageKind.NORMAL, "")
                }
                messages.updateMessageText(session, id, finalText.toString(), streaming = true)
            }
        }
    }

    private class LoopState {
        var iteration = 0
        var toolsRun = 0
        var actionNudged = false
        var parseRetryCount = 0
        var thoughtOnlyContinues = 0
    }

    private companion object {
        const val MAX_ITERATIONS = 12
        const val MAX_PARSE_RETRIES = 3
        const val MAX_THOUGHT_ONLY_CONTINUES = 3
        const val MAX_CARD_OUTPUT = 4000
        const val MAX_MODEL_OUTPUT = 24000
        const val STREAM_FLUSH_MS = 40L

        const val FINAL_ONLY_NUDGE =
            "You sent a JSON final answer without any file tool actions. " +
                "Implement the plan now using edit_file/create_file tools in a JSON actions array."
        const val PROSE_NUDGE =
            "You are in Agent mode. Make the changes now using the file tools; " +
                "do not just describe them. A plan may already exist — implement it."
    }
}
