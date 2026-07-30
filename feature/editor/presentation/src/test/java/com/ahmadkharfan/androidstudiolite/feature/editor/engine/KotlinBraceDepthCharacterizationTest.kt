package com.ahmadkharfan.androidstudiolite.feature.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class KotlinBraceDepthCharacterizationTest {

    @Test
    fun `brace depth ignores braces in every quoted and commented form`() {
        val markedText = "fun demo() {\n" +
            "    val quoted = \"{ }\"\n" +
            "    val character = '}'\n" +
            "    val raw = \"\"\"{ }\"\"\"\n" +
            "    // }\n" +
            "    /* { } */\n" +
            "    if (true) {\n" +
            "        §\n" +
            "    }\n" +
            "}"

        assertEquals(2, depthAtMarker(markedText))
    }

    @Test
    fun `brace depth retains negative and unterminated construct behavior`() {
        assertEquals(-1, depthAtMarker("}§"))
        assertEquals(1, depthAtMarker("{ /* ignored }§"))
        assertEquals(1, depthAtMarker("{ \"ignored }§"))
        assertEquals(1, depthAtMarker("{ \"\"\"ignored }§"))
    }

    private fun depthAtMarker(markedText: String): Int {
        val caret = markedText.indexOf('§')
        val text = markedText.replace("§", "")
        return KotlinLexUtil.braceDepthBefore(text, caret)
    }
}
