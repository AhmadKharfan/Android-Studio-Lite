package com.ahmadkharfan.androidstudiolite.data.gradle

import com.ahmadkharfan.androidstudiolite.data.gradle.parse.GToken
import com.ahmadkharfan.androidstudiolite.data.gradle.parse.GTokenType
import com.ahmadkharfan.androidstudiolite.data.gradle.parse.GradleScriptScanner
import org.junit.Assert.assertEquals
import org.junit.Test

class GradleScriptTokenizationCharacterizationTest {

    @Test
    fun `mixed script produces the complete current token sequence`() {
        val text = "name\r\n// line\n/* block */ 12_3 `when` == = {}()[] .,\"q\\\"r\" 's' @"

        fun token(fragment: String, type: GTokenType, value: String = fragment): GToken {
            val start = text.indexOf(fragment)
            return GToken(type, value, start, start + fragment.length)
        }
        val commentNewline = text.indexOf('\n', text.indexOf('\n') + 1)

        assertEquals(
            listOf(
                token("name", GTokenType.IDENT),
                token("\n", GTokenType.NEWLINE),
                GToken(GTokenType.NEWLINE, "\n", commentNewline, commentNewline + 1),
                token("12_3", GTokenType.NUMBER),
                token("`when`", GTokenType.IDENT, "when"),
                token("==", GTokenType.OTHER),
                GToken(GTokenType.EQ, "=", text.lastIndexOf('='), text.lastIndexOf('=') + 1),
                token("{", GTokenType.LBRACE),
                token("}", GTokenType.RBRACE),
                token("(", GTokenType.LPAREN),
                token(")", GTokenType.RPAREN),
                token("[", GTokenType.LBRACKET),
                token("]", GTokenType.RBRACKET),
                token(".", GTokenType.DOT),
                token(",", GTokenType.COMMA),
                token("\"q\\\"r\"", GTokenType.STRING),
                token("'s'", GTokenType.STRING),
                token("@", GTokenType.OTHER),
            ),
            GradleScriptScanner.tokenize(text),
        )
    }
}
