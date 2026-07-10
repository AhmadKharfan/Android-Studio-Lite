package com.example.androidstudiolite.feature.editor.engine.xml

import com.example.androidstudiolite.feature.editor.engine.DiagnosticSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XmlBackendTest {
    private fun diag(text: String, path: String = "layout/a.xml") = XmlBackend.analyze(text, path)
    private fun labels(text: String, path: String = "layout/a.xml") =
        XmlBackend.complete(text, text.length, path).items.map { it.label }


    @Test
    fun cleanXmlHasNoWellFormednessErrors() {
        assertTrue(diag("<a><b/><c>x</c></a>", "raw/a.xml").isEmpty())
    }

    @Test
    fun unclosedTagMessageMatchesCodeAssist() {
        val d = diag("<LinearLayout>\n  <TextView\n    android:layout_width=\"wrap_content\"\n    android:layout_height=\"wrap_content\"/>\n", "raw/a.xml")
        val err = d.first { it.code == "xml.unclosedTag" }
        assertEquals("Missing closing tag </LinearLayout>", err.message)
    }

    @Test
    fun strayCloseTagFlagged() {
        val d = diag("<a></b>", "raw/a.xml")
        assertTrue(d.any { it.code == "xml.strayClose" && it.message == "Unexpected closing tag" })
    }

    @Test
    fun unterminatedAttributeValueFlagged() {
        val d = diag("<View android:id=\"abc />", "raw/a.xml")
        assertTrue(d.any { it.code == "xml.unterminatedValue" })
    }

    @Test
    fun prologAndCommentsIgnored() {
        assertTrue(diag("<?xml version=\"1.0\"?>\n<!-- a </b> comment -->\n<root/>", "raw/a.xml").isEmpty())
    }


    @Test
    fun missingNamespaceFlagged() {
        val d = XmlBackend.analyze("<LinearLayout android:orientation=\"vertical\"/>", "raw/a.xml")
        val err = d.first { it.code == "android.missingNamespace" }
        assertEquals("Missing xmlns:android namespace declaration", err.message)
        assertEquals(DiagnosticSeverity.Error, err.severity)
    }

    @Test
    fun missingSizeFlaggedInLayout() {
        val d = XmlBackend.analyze(
            "<LinearLayout xmlns:android=\"http://schemas.android.com/apk/res/android\"><TextView/></LinearLayout>",
            "app/src/main/res/layout/main.xml",
        )
        assertTrue(d.any { it.code == "android.missingSize" && it.message == "<TextView> is missing android:layout_width" })
    }

    @Test
    fun hardcodedTextFlaggedInLayout() {
        val d = XmlBackend.analyze(
            "<TextView xmlns:android=\"http://schemas.android.com/apk/res/android\" android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:text=\"Hello\"/>",
            "app/src/main/res/layout/main.xml",
        )
        assertTrue(d.any { it.code == "android.hardcodedText" && it.severity == DiagnosticSeverity.Warning })
    }


    @Test
    fun completesTagName() {
        assertTrue(labels("<Te").contains("TextView"))
    }

    @Test
    fun completesAttributeNameByLocalName() {
        assertTrue(labels("<TextView layout_w").contains("android:layout_width"))
    }

    @Test
    fun completesAttributeValue() {
        assertTrue(labels("<TextView android:layout_width=\"").contains("match_parent"))
    }

    @Test
    fun manifestTagsInManifest() {
        assertTrue(labels("<manifest><activ", "AndroidManifest.xml").contains("activity"))
    }

    @Test
    fun noCompletionInTextContent() {
        assertTrue(labels("<TextView>hello").isEmpty())
    }

    @Test
    fun doesNotResuggestExistingAttribute() {
        val labels = labels("<TextView android:id=\"@+id/x\" andr")
        assertTrue(labels.none { it == "android:id" })
    }
}
