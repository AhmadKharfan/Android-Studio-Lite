package com.ahmadkharfan.androidstudiolite.data.ai

import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction
import com.ahmadkharfan.androidstudiolite.domain.model.AgentToolResult
import com.ahmadkharfan.androidstudiolite.domain.model.AiAgentSettings
import com.ahmadkharfan.androidstudiolite.domain.model.AiProviderConfig
import com.ahmadkharfan.androidstudiolite.domain.model.ApiKeyStatus
import com.ahmadkharfan.androidstudiolite.domain.model.ChatRole
import com.ahmadkharfan.androidstudiolite.domain.model.ChatThread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChatAgentSupportTest {

    private fun provider(id: String, status: ApiKeyStatus = ApiKeyStatus.VALID) =
        AiProviderConfig(id = id, name = id, icon = "", description = "", status = status)

    @Test
    fun pathOrNullReturnsPathForFileActionsAndNullForSearch() {
        assertEquals("a/b.kt", AgentAction.EditFile("a/b.kt", "x").pathOrNull())
        assertEquals("dir", AgentAction.ListDir("dir").pathOrNull())
        assertNull(AgentAction.Search("query").pathOrNull())
    }

    @Test
    fun summaryDescribesEachActionType() {
        assertEquals("Edit a.kt", AgentAction.EditFile("a.kt", "x").summary())
        assertEquals("Rename a.kt -> b.kt", AgentAction.Rename("a.kt", "b.kt").summary())
        assertEquals("Search \"todo\"", AgentAction.Search("todo").summary())
    }

    @Test
    fun coalesceTurnsMergesConsecutiveSameRoleTurns() {
        val turns = listOf(
            LlmChatTurn(ChatRole.USER, "one"),
            LlmChatTurn(ChatRole.USER, "two"),
            LlmChatTurn(ChatRole.AI, "reply"),
        )
        val merged = coalesceTurns(turns)

        assertEquals(2, merged.size)
        assertEquals(LlmChatTurn(ChatRole.USER, "one\n\ntwo"), merged[0])
        assertEquals(LlmChatTurn(ChatRole.AI, "reply"), merged[1])
    }

    @Test
    fun formatToolResultsLabelsStatusAndTruncatesOutput() {
        val results = listOf(
            AgentToolResult(AgentAction.ReadFile("a.kt"), ok = true, output = "0123456789"),
            AgentToolResult(AgentAction.Search("q"), ok = false, output = "boom"),
        )
        val text = formatToolResults(results, outputLimit = 4)

        assertEquals(
            """
            TOOL RESULTS:
            [read_file a.kt] OK:
            0123
            [search ] ERROR:
            boom

            """.trimIndent(),
            text,
        )
    }

    @Test
    fun resolveChatProviderPrefersThreadPinnedProviderWhenValid() {
        val settings = AiAgentSettings(
            providers = listOf(provider("anthropic"), provider("gemini")),
            activeProviderId = "anthropic",
        )
        val thread = ChatThread(id = "t", title = "", createdAt = 0, updatedAt = 0, messages = emptyList(), providerId = "gemini")

        assertEquals("gemini", resolveChatProvider(settings, thread)?.id)
    }

    @Test
    fun resolveChatProviderFallsBackToActiveWhenThreadProviderInvalidOrAbsent() {
        val settings = AiAgentSettings(
            providers = listOf(provider("anthropic"), provider("gemini", ApiKeyStatus.INVALID)),
            activeProviderId = "anthropic",
        )
        val thread = ChatThread(id = "t", title = "", createdAt = 0, updatedAt = 0, messages = emptyList(), providerId = "gemini")

        assertEquals("anthropic", resolveChatProvider(settings, thread)?.id)
    }

    @Test
    fun resolveChatProviderReturnsNullWhenAgentDisabled() {
        val settings = AiAgentSettings(enabled = false, providers = listOf(provider("anthropic")), activeProviderId = "anthropic")
        assertNull(resolveChatProvider(settings, thread = null))
    }
}
