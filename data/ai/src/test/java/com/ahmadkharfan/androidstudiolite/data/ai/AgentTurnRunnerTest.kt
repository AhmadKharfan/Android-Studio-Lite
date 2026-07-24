package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.data.ai.agent.AgentTools
import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction
import com.ahmadkharfan.androidstudiolite.domain.model.AgentToolResult
import com.ahmadkharfan.androidstudiolite.domain.model.AiAgentSettings
import com.ahmadkharfan.androidstudiolite.domain.model.AiProviderConfig
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThread
import com.ahmadkharfan.androidstudiolite.domain.model.ToolCallStatus
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private class FakeStreamer(private val responses: MutableList<String>, private val error: Throwable? = null) : LlmStreamer {
    override fun stream(request: LlmStreamRequest, onDelta: (String) -> Unit): String {
        error?.let { throw it }
        return responses.removeAt(0)
    }
}

private class FakeTools : AgentTools {
    val ran = mutableListOf<AgentAction>()
    override suspend fun outline(projectId: String, maxEntries: Int): String = ""
    override suspend fun sourcePackagePrefix(projectId: String): String? = null
    override suspend fun projectLanguage(projectId: String): String = "Kotlin"
    override suspend fun projectRoot(projectId: String): File = File(".")
    override suspend fun readTextOrNull(projectId: String, relativePath: String): String? = null
    override suspend fun run(projectId: String, action: AgentAction): AgentToolResult {
        ran += action
        return AgentToolResult(action, ok = true, output = "applied")
    }
    override fun normalizeAction(root: File, action: AgentAction): AgentAction = action
}

class AgentTurnRunnerTest {

    private var counter = 0
    private val editor = ChatMessageEditor(ids = ChatIdGenerator { "id-${++counter}" }, clock = ChatClock { "12:00 PM" })
    private val provider = AiProviderConfig(id = "anthropic", name = "Anthropic", icon = "", description = "")

    private fun sessionWithUserMessage(text: String): ChatSession = ChatSession("p").apply {
        threads.value = listOf(ChatThread("t", "", 0, 0, emptyList()))
        activeThreadId.value = "t"
        editor.appendUserMessage(this, text)
    }

    private fun runner(responses: List<String>, tools: AgentTools = FakeTools(), error: Throwable? = null) =
        AgentTurnRunner(
            streamer = FakeStreamer(responses.toMutableList(), error),
            tools = tools,
            messages = editor,
            pendingApprovals = ConcurrentHashMap(),
            io = Dispatchers.Unconfined,
        )

    private fun settings(autoApply: Boolean) = AiAgentSettings(autoApply = autoApply)

    private fun request(session: ChatSession, mode: ChatMode, autoApply: Boolean, userText: String) =
        AgentRequest(session, provider, "key", "m", mode, settings(autoApply), null, userText)

    @Test
    fun finalAnswerIsAppendedAsAiMessage() = runBlocking {
        val session = sessionWithUserMessage("what is this?")
        runner(listOf("""{"final":"It is an Android IDE."}""")).run(request(session, ChatMode.ASK, autoApply = false, "what is this?"))

        val last = session.activeMessages().last()
        assertEquals(ChatRole.AI, last.role)
        assertEquals("It is an Android IDE.", last.text)
    }

    @Test
    fun actionsTurnRunsToolThenFinalTurnCompletes() = runBlocking {
        val session = sessionWithUserMessage("edit a.kt")
        val tools = FakeTools()
        runner(
            responses = listOf(
                """{"thought":"editing","actions":[{"tool":"edit_file","path":"a.kt","content":"x"}]}""",
                """{"final":"Done editing."}""",
            ),
            tools = tools,
        ).run(request(session, ChatMode.AGENT, autoApply = true, "edit a.kt"))

        assertEquals(1, tools.ran.size)
        assertTrue(tools.ran.first() is AgentAction.EditFile)

        val toolMessage = session.activeMessages().first { it.toolCall != null }
        assertEquals("edit_file", toolMessage.toolCall!!.tool)
        assertEquals(ToolCallStatus.DONE, toolMessage.toolCall!!.status)
        assertEquals("Done editing.", session.activeMessages().last().text)
    }

    @Test
    fun streamFailureSurfacesAsRequestFailedMessage() = runBlocking {
        val session = sessionWithUserMessage("hello")
        runner(responses = emptyList(), error = AiLlmException("network down")).run(request(session, ChatMode.ASK, autoApply = false, "hello"))

        val last = session.activeMessages().last()
        assertEquals(ChatRole.AI, last.role)
        assertTrue(last.text.contains("Request failed"))
        assertTrue(last.text.contains("network down"))
    }
}
