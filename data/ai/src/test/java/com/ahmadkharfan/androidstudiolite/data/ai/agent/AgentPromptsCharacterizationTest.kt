package com.ahmadkharfan.androidstudiolite.data.ai.agent

import com.ahmadkharfan.androidstudiolite.domain.model.ChatMode
import org.junit.Assert.assertEquals
import org.junit.Test

class AgentPromptsCharacterizationTest {

    @Test
    fun `agent prompt includes write tools and all project context`() {
        val generatedPrompt = AgentProtocol.systemPrompt(
            userInstructions = "  Keep package names.  ",
            projectOutline = "app/\n  src/main/\n",
            activeFilePath = "app/src/main/java/example/MainActivity.java",
            mode = ChatMode.AGENT,
            sourcePackagePrefix = "app/src/main/java/example/",
            projectLanguage = "Java",
        )

        assertMatchesFixture("agent", generatedPrompt)
    }

    @Test
    fun `ask prompt omits write tools and blank optional context`() {
        val generatedPrompt = AgentProtocol.systemPrompt(
            userInstructions = "   ",
            projectOutline = "settings.gradle.kts\n",
            activeFilePath = null,
            mode = ChatMode.ASK,
            sourcePackagePrefix = "  ",
            projectLanguage = "Java + Kotlin",
        )

        assertMatchesFixture("ask", generatedPrompt)
    }

    @Test
    fun `plan prompt includes planning rules and available project context`() {
        val generatedPrompt = AgentProtocol.systemPrompt(
            userInstructions = "  Focus on offline behavior.\n  Preserve public APIs.  ",
            projectOutline = "feature/\n  editor/\n",
            activeFilePath = "feature/editor/src/main/Main.kt",
            mode = ChatMode.PLAN,
            sourcePackagePrefix = null,
            projectLanguage = "Kotlin",
        )

        assertMatchesFixture("plan", generatedPrompt)
    }

    private fun assertMatchesFixture(fixtureName: String, generatedPrompt: String) {
        val expectedPrompt = requireNotNull(
            javaClass.getResourceAsStream("/agent-prompts/$fixtureName.txt"),
        ).bufferedReader().use { it.readText() }
        assertEquals(expectedPrompt, generatedPrompt)
    }
}
