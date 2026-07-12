package com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml

import com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticCodes
import com.ahmadkharfan.androidstudiolite.feature.editor.engine.DiagnosticSeverity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Supplementary coverage for the cleanroom XML backend: behaviors that the original
 * [XmlBackendTest] exercises only implicitly (or not at all), pinned so the rewrite's contract is
 * explicit — diagnostic codes/messages/severities, negative lint cases, and completion ranges.
 */
class XmlBackendCleanroomTest {

    private val layout = "app/src/main/res/layout/main.xml"
    private val ns = "xmlns:android=\"http://schemas.android.com/apk/res/android\""

    private fun diag(text: String, path: String = "raw/a.xml") = XmlBackend.analyze(text, path)
    private fun labels(text: String, path: String = layout) =
        XmlBackend.complete(text, text.length, path).items.map { it.label }

    // region well-formedness

    @Test
    fun unquotedAttributeValueFlagged() {
        assertTrue(diag("<a x=y>").any { it.code == DiagnosticCodes.XML_UNQUOTED_VALUE })
    }

    @Test
    fun malformedStartTagFlagged() {
        val d = diag("<a <b></b>")
        val err = d.first { it.code == DiagnosticCodes.XML_MALFORMED_TAG }
        assertEquals("Malformed start tag for <a>", err.message)
    }

    @Test
    fun innerUnclosedTagNamesItself() {
        val d = diag("<a><b></a>")
        assertTrue(d.any { it.code == DiagnosticCodes.XML_UNCLOSED_TAG && it.message == "Missing closing tag </b>" })
    }

    @Test
    fun nestedSelfClosingIsClean() {
        assertTrue(diag("<a><b/><c><d/></c></a>").isEmpty())
    }

    @Test
    fun wellFormednessIssuesAreErrors() {
        assertTrue(diag("<a></b>").all { it.severity == DiagnosticSeverity.Error })
    }

    // endregion

    // region android lint

    @Test
    fun bothMissingDimensionsReported() {
        val d = XmlBackend.analyze("<FrameLayout $ns><View/></FrameLayout>", layout)
        assertTrue(d.any { it.code == DiagnosticCodes.ANDROID_MISSING_SIZE && it.message == "<View> is missing android:layout_width" })
        assertTrue(d.any { it.code == DiagnosticCodes.ANDROID_MISSING_SIZE && it.message == "<View> is missing android:layout_height" })
    }

    @Test
    fun stringResourceReferenceIsNotHardcoded() {
        val d = XmlBackend.analyze(
            "<TextView $ns android:layout_width=\"wrap_content\" android:layout_height=\"wrap_content\" android:text=\"@string/hello\"/>",
            layout,
        )
        assertFalse(d.any { it.code == DiagnosticCodes.ANDROID_HARDCODED_TEXT })
    }

    @Test
    fun declaredNamespaceIsNotFlagged() {
        val d = XmlBackend.analyze("<LinearLayout $ns android:orientation=\"vertical\"/>", "raw/a.xml")
        assertFalse(d.any { it.code == DiagnosticCodes.ANDROID_MISSING_NAMESPACE })
    }

    @Test
    fun missingAppNamespaceFlagged() {
        val d = XmlBackend.analyze("<LinearLayout $ns app:layout_constraintTop_toTopOf=\"parent\"/>", "raw/a.xml")
        assertTrue(d.any { it.code == DiagnosticCodes.ANDROID_MISSING_NAMESPACE && it.message == "Missing xmlns:app namespace declaration" })
    }

    @Test
    fun layoutLintSkippedOutsideLayoutFiles() {
        // A view-like tag with no dimensions lives in a non-layout resource: no size warning.
        val d = XmlBackend.analyze("<TextView $ns/>", "raw/a.xml")
        assertFalse(d.any { it.code == DiagnosticCodes.ANDROID_MISSING_SIZE })
    }

    // endregion

    // region completion

    @Test
    fun completesAttributeValueRightAfterEquals() {
        assertTrue(labels("<TextView android:orientation=").contains("vertical"))
    }

    @Test
    fun parentContainerRoutesToManifestVocabulary() {
        // Not named AndroidManifest.xml, but the parent element makes it a manifest context.
        assertTrue(labels("<application><activ", "config/some.xml").contains("activity"))
    }

    @Test
    fun noCompletionInsideNestedContent() {
        assertTrue(labels("<LinearLayout><TextView>text").isEmpty())
    }

    @Test
    fun replacementRangeCoversTypedTagName() {
        assertEquals(1 to 3, XmlBackend.replacementRangeAt("<Te", 3, layout))
    }

    @Test
    fun autoPopupOpensOnOpenAngle() {
        assertTrue(XmlBackend.shouldAutoPopup("", 0, '<', layout))
    }

    // endregion
}
