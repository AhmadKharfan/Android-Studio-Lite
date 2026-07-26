package com.ahmadkharfan.androidstudiolite.feature.editor.engine

import org.junit.Assert.assertEquals
import org.junit.Test

class EditorCompletionAcceptanceCharacterizationTest {

    @Test
    fun `xml attribute acceptance replaces the typed name and places caret inside quotes`() {
        val session = EditorSession(
            initialText = "<TextView android:lay",
            language = EditorLanguage.Xml,
            filePath = "res/layout/main.xml",
        ).also { it.setCaret(it.text.length) }
        val controller = EditorCompletionController()
        val item = controller.query(session).first { it.label == "android:layout_width" }

        val inserted = controller.accept(session, item)

        assertEquals("android:layout_width=\"\"", inserted)
        assertEquals("<TextView android:layout_width=\"\"", session.text)
        assertEquals(32, session.selection.caret)
    }
}
