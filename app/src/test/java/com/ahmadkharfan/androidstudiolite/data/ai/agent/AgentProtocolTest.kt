package com.ahmadkharfan.androidstudiolite.data.ai.agent

import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentProtocolTest {

    @Test
    fun `parses final answer`() {
        val turn = AgentProtocol.parse("""{"thought":"done","final":"All set."}""")
        assertTrue(turn is AgentTurn.Final)
        assertEquals("All set.", (turn as AgentTurn.Final).text)
    }

    @Test
    fun `parses single action batch`() {
        val turn = AgentProtocol.parse(
            """{"thought":"look","actions":[{"tool":"read_file","path":"app/build.gradle.kts"}]}""",
        )
        turn as AgentTurn.Actions
        assertEquals(1, turn.actions.size)
        val action = turn.actions.first()
        assertTrue(action is AgentAction.ReadFile)
        assertEquals("app/build.gradle.kts", (action as AgentAction.ReadFile).path)
    }

    @Test
    fun `parses multiple actions in order`() {
        val turn = AgentProtocol.parse(
            """
            {"actions":[
              {"tool":"create_dir","path":"app/src/main/java/x/model"},
              {"tool":"create_file","path":"app/src/main/java/x/model/User.kt","content":"data class User(val id: String)"}
            ]}
            """.trimIndent(),
        )
        turn as AgentTurn.Actions
        assertEquals(2, turn.actions.size)
        assertTrue(turn.actions[0] is AgentAction.CreateDir)
        val create = turn.actions[1] as AgentAction.CreateFile
        assertEquals("app/src/main/java/x/model/User.kt", create.path)
        assertTrue(create.content.contains("data class User"))
    }

    @Test
    fun `tolerates markdown fences and surrounding prose`() {
        val raw = "Sure, here you go:\n```json\n{\"final\":\"Hi there\"}\n```\nThanks!"
        val turn = AgentProtocol.parse(raw)
        assertEquals("Hi there", (turn as AgentTurn.Final).text)
    }

    @Test
    fun `accepts snake and camel case rename fields`() {
        val snake = AgentProtocol.parse("""{"actions":[{"tool":"rename","path":"a/b.kt","new_name":"c.kt"}]}""")
        val camel = AgentProtocol.parse("""{"actions":[{"tool":"rename","path":"a/b.kt","newName":"c.kt"}]}""")
        val s = (snake as AgentTurn.Actions).actions.first() as AgentAction.Rename
        val c = (camel as AgentTurn.Actions).actions.first() as AgentAction.Rename
        assertEquals("c.kt", s.newName)
        assertEquals("c.kt", c.newName)
    }

    @Test
    fun `move accepts new_parent`() {
        val turn = AgentProtocol.parse(
            """{"actions":[{"tool":"move","path":"a/b.kt","new_parent":"a/sub"}]}""",
        )
        val move = (turn as AgentTurn.Actions).actions.first() as AgentAction.Move
        assertEquals("a/sub", move.newParent)
    }

    @Test
    fun `non json reply becomes final text`() {
        val turn = AgentProtocol.parse("I cannot help with that.")
        assertTrue(turn is AgentTurn.Final)
        assertEquals("I cannot help with that.", (turn as AgentTurn.Final).text)
    }

    @Test
    fun `unknown tool is ignored leaving valid actions`() {
        val turn = AgentProtocol.parse(
            """{"actions":[{"tool":"bogus","path":"x"},{"tool":"delete","path":"old.txt"}]}""",
        )
        turn as AgentTurn.Actions
        assertEquals(1, turn.actions.size)
        assertTrue(turn.actions.first() is AgentAction.Delete)
    }

    @Test
    fun `agent mode prompt offers write tools`() {
        val prompt = AgentProtocol.systemPrompt("", "outline", null, ChatMode.AGENT)
        assertTrue(prompt.contains("edit_file"))
        assertTrue(prompt.contains("create_file"))
    }

    @Test
    fun `ask mode prompt is read-only`() {
        val prompt = AgentProtocol.systemPrompt("", "outline", null, ChatMode.ASK)
        assertTrue(prompt.contains("READ-ONLY"))
        assertFalse(prompt.contains("edit_file"))
        assertTrue(prompt.contains("read_file"))
    }

    @Test
    fun `plan mode prompt asks for a plan and blocks edits`() {
        val prompt = AgentProtocol.systemPrompt("", "outline", null, ChatMode.PLAN)
        assertTrue(prompt.contains("plan"))
        assertFalse(prompt.contains("edit_file"))
    }
}
