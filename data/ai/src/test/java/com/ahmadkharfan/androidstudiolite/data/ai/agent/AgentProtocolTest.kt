package com.ahmadkharfan.androidstudiolite.data.ai.agent

import com.ahmadkharfan.androidstudiolite.domain.model.AgentAction
import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentProtocolTest {

    @Test
    fun `parseExecutionTurn prefers actions over final in agent mode`() {
        val raw = """{"thought":"x","final":"Done.","actions":[{"tool":"edit_file","path":"a.kt","content":"fun main(){}"}]}"""
        val turn = AgentProtocol.parseExecutionTurn(raw, preferActions = true)
        turn as AgentTurn.Actions
        assertEquals(1, turn.actions.size)
        assertTrue(turn.actions.first() is AgentAction.EditFile)
    }

    @Test
    fun `salvages create_file when content contains unescaped kotlin string quotes`() {
        val raw = """
            {"thought":"Creating file","actions":[{"tool":"create_file","path":"app/src/Foo.kt","content":"package com.example

            import androidx.compose.material.Text

            @Composable
            fun Foo() {
                Text(text = "Hello")
            }
            "}}]}
        """.trimIndent()
        val turn = AgentProtocol.parseExecutionTurn(raw, preferActions = true)
        turn as AgentTurn.Actions
        assertEquals(1, turn.actions.size)
        val create = turn.actions.first() as AgentAction.CreateFile
        assertEquals("app/src/Foo.kt", create.path)
        assertTrue(create.content.contains("Text(text = \"Hello\")"))
        assertTrue(create.content.contains("package com.example"))
    }

    @Test
    fun `isThoughtOnlyTurn detects thought without actions or final`() {
        val raw = """{"thought":"Next I'll create the ViewModel."}"""
        assertTrue(AgentProtocol.isThoughtOnlyTurn(raw))
        assertFalse(
            AgentProtocol.isThoughtOnlyTurn(
                """{"thought":"done","final":"All set."}""",
            ),
        )
    }

    @Test
    fun `shouldAutoContinueImplementation when tools already ran`() {
        assertTrue(
            AgentProtocol.shouldAutoContinueImplementation(
                ChatMode.AGENT,
                toolsRun = 1,
                userText = "hello",
            ),
        )
    }

    @Test
    fun `salvages multiple create_file actions with unescaped kotlin quotes`() {
        val raw = """
            {"thought":"Creating files","actions":[
              {"tool":"create_file","path":"app/A.kt","content":"package a

            fun A() { Text(text = "A") }
            "},
              {"tool":"create_file","path":"app/B.kt","content":"package b

            fun B() { Text(text = "B") }
            "}
            ]}
        """.trimIndent()
        val turn = AgentProtocol.parseExecutionTurn(raw, preferActions = true)
        turn as AgentTurn.Actions
        assertEquals(2, turn.actions.size)
        assertTrue(turn.actions[0].toString().contains("A.kt"))
        assertTrue(turn.actions[1].toString().contains("B.kt"))
    }

    @Test
    fun `extractLooseJsonStringValue respects searchEnd for multi action payloads`() {
        val raw = """{"actions":[{"tool":"edit_file","path":"a.kt","content":"line1"},{"tool":"edit_file","path":"b.kt","content":"line2"}]}"""
        val firstStart = raw.indexOf("line1")
        val secondTool = raw.indexOf("\"tool\"", firstStart + 1)
        val content = AgentProtocol.extractLooseJsonStringValue(raw, firstStart, secondTool)
        assertEquals("line1", content)
    }

    @Test
    fun `extractLooseJsonStringValue reads content before action close`() {
        val raw = """{"actions":[{"tool":"edit_file","path":"a.kt","content":"line1\nline2"}]}"""
        val start = raw.indexOf("line1")
        val content = AgentProtocol.extractLooseJsonStringValue(raw, start)
        assertEquals("line1\nline2", content)
    }

    @Test
    fun `parseExecutionTurn parses large edit_file payload with braces in content`() {
        val raw = """
            {"thought":"Editing","actions":[{"tool":"edit_file","path":"app/MainActivity.kt","content":"package com.example\nclass MainActivity {\n    fun onCreate() {\n        setContent { Text(\"Hello\") }\n    }\n}"}]}
        """.trimIndent()
        val turn = AgentProtocol.parseExecutionTurn(raw, preferActions = true)
        turn as AgentTurn.Actions
        val edit = turn.actions.first() as AgentAction.EditFile
        assertEquals("app/MainActivity.kt", edit.path)
        assertTrue(edit.content.contains("setContent"))
    }

    @Test
    fun `actions as single object is accepted`() {
        val turn = AgentProtocol.parseExecutionTurn(
            """{"thought":"go","actions":{"tool":"edit_file","path":"x.kt","content":"// ok"}}""",
            preferActions = true,
        )
        turn as AgentTurn.Actions
        assertEquals(1, turn.actions.size)
    }

    @Test
    fun `sanitizeDisplayText never returns raw protocol json`() {
        val raw = """{"thought":"hi","actions":[{"tool":"read_file","path":"a.kt"}]}"""
        val text = AgentProtocol.sanitizeDisplayText(raw)
        assertFalse(AgentProtocol.looksLikeProtocolJson(text))
        assertEquals("hi", text)
    }

    @Test
    fun `repairJsonStringEscapes fixes literal newlines in strings`() {
        val broken = """{"thought":"line1
line2","actions":[]}"""
        val repaired = AgentProtocol.repairJsonStringEscapes(broken)
        val turn = AgentProtocol.parseExecutionTurn(repaired, preferActions = true)
        assertTrue(turn is AgentTurn.Final)
        assertEquals("line1\nline2", (turn as AgentTurn.Final).text)
    }

    @Test
    fun `salvageActions extracts edit_file from malformed outer json`() {
        val raw = """not json {"thought":"x","actions":[{"tool":"edit_file","path":"Main.kt","content":"fun main(){}"} extra"""
        val salvaged = AgentProtocol.salvageActions(raw)
        assertEquals(1, salvaged.size)
        assertTrue(salvaged.first() is AgentAction.EditFile)
    }

    @Test
    fun `parseExecutionTurn salvages actions when root json is invalid`() {
        val raw = """{"thought":"edit","actions":[{"tool":"edit_file","path":"Main.kt","content":"package x
class Y {}"}]}"""
        val turn = AgentProtocol.parseExecutionTurn(raw, preferActions = true)
        assertTrue(turn is AgentTurn.Actions)
        assertEquals(1, (turn as AgentTurn.Actions).actions.size)
    }

    @Test
    fun `isUnparsedProtocolResponse detects truncated json`() {
        val raw = """{"thought":"x","actions":[{"tool":"edit_file","path":"a.kt","content":"partial"""
        assertTrue(AgentProtocol.isUnparsedProtocolResponse(raw, AgentProtocol.PARSE_FAILURE_MESSAGE))
    }

    @Test
    fun `reviewPlanPrompt includes custom user focus`() {
        val prompt = AgentProtocol.reviewPlanPrompt("Check ViewModel lifecycle and Compose navigation")
        assertTrue(prompt.contains("The user wants you to focus on:"))
        assertTrue(prompt.contains("ViewModel lifecycle"))
    }

    @Test
    fun `reviewPlanPrompt uses default focus when instructions blank`() {
        val prompt = AgentProtocol.reviewPlanPrompt("   ")
        assertTrue(prompt.contains("gaps, risks, missing steps"))
    }

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
