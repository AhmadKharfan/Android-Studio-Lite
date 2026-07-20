package com.ahmadkharfan.androidstudiolite.feature.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorLanguageTest {

    @Test
    fun `Java files use Java editor behavior and label`() {
        val language = EditorLanguage.fromFileName("MainActivity.java")

        assertEquals(EditorLanguage.Java, language)
        assertEquals("Java", language.displayName)
    }

    @Test
    fun `Java session accepts edits and Java formatting`() {
        val session = EditorSession(
            "public class Main {\npublic void run() {\n}\n}",
            EditorLanguage.Java,
        )
        session.setCaret(session.text.indexOf("run"))
        session.replaceRange(session.selection.start, session.selection.end, "static ")

        val formatted = CodeFormatter.reformat(session.text, 4, session.language)

        assertEquals(EditorLanguage.Java, session.language)
        assertEquals(true, formatted.contains("static run"))
        assertEquals(true, formatted.contains("    public void"))
    }
}
