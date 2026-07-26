package com.ahmadkharfan.androidstudiolite.feature.editor.engine.xml

import com.ahmadkharfan.androidstudiolite.core.xml.XmlParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XmlCompletionAnalyzerTest {

    @Test
    fun tagNamePositions_replaceOnlyTheTypedName() {
        val emptyName = positionAt("<|")
        assertEquals(XmlCompletionKind.TAG_NAME, emptyName.kind)
        assertEquals(null, emptyName.tag)
        assertReplacement(emptyName, "", 1, 1)

        val partialName = positionAt("<Text|")
        assertEquals(XmlCompletionKind.TAG_NAME, partialName.kind)
        assertEquals("Text", partialName.tag)
        assertReplacement(partialName, "Text", 1, 5)
    }

    @Test
    fun attributeNamePositions_replaceOnlyTheCurrentAttribute() {
        val emptyName = positionAt("<TextView |")
        assertEquals(XmlCompletionKind.ATTRIBUTE_NAME, emptyName.kind)
        assertEquals("TextView", emptyName.tag)
        assertReplacement(emptyName, "", 10, 10)

        val partialName = positionAt("<TextView android:lay|")
        assertEquals(XmlCompletionKind.ATTRIBUTE_NAME, partialName.kind)
        assertEquals("TextView", partialName.tag)
        assertEquals(emptySet<String>(), partialName.existingAttributes)
        assertReplacement(partialName, "android:lay", 10, 21)
    }

    @Test
    fun attributeValuePositions_reportAttributeAndValueReplacementRange() {
        val emptyValue = positionAt("<TextView android:layout_width=\"|")
        assertEquals(XmlCompletionKind.ATTRIBUTE_VALUE, emptyValue.kind)
        assertEquals("android:layout_width", emptyValue.attributeName)
        assertEquals(setOf("android:layout_width"), emptyValue.existingAttributes)
        assertReplacement(emptyValue, "", 32, 32)

        val partialValue = positionAt("<TextView android:layout_width=\"mat|")
        assertEquals(XmlCompletionKind.ATTRIBUTE_VALUE, partialValue.kind)
        assertEquals("android:layout_width", partialValue.attributeName)
        assertReplacement(partialValue, "mat", 32, 35)
    }

    @Test
    fun textBetweenElements_isClassifiedAsText() {
        val position = positionAt("<TextView/> |<Button/>")

        assertEquals(XmlCompletionKind.TEXT, position.kind)
        assertEquals(null, position.tag)
        assertReplacement(position, "", 12, 12)
    }

    @Test
    fun closingTagCommentAndProcessingInstruction_areUnknown() {
        assertUnknown("<TextView></Text|View>")
        assertUnknown("<!-- comm|ent -->")
        assertUnknown("<?xml ver|sion=\"1.0\"?>")
    }

    @Test
    fun completedAttributes_areReportedWhileTypingANewAttribute() {
        val position = positionAt(
            "<TextView android:id=\"@+id/title\" android:text=\"Title\" android:lay|",
        )

        assertEquals(XmlCompletionKind.ATTRIBUTE_NAME, position.kind)
        assertEquals(setOf("android:id", "android:text"), position.existingAttributes)
        assertReplacement(position, "android:lay", 55, 66)
    }

    @Test
    fun singleQuotedValue_isClassifiedAsAttributeValue() {
        val position = positionAt("<TextView android:text='hel|")

        assertEquals(XmlCompletionKind.ATTRIBUTE_VALUE, position.kind)
        assertEquals("android:text", position.attributeName)
        assertReplacement(position, "hel", 24, 27)
    }

    @Test
    fun greaterThanInsideQuotedValue_doesNotEndTheTag() {
        val position = positionAt("<TextView android:text=\"a>b|")

        assertEquals(XmlCompletionKind.ATTRIBUTE_VALUE, position.kind)
        assertEquals("android:text", position.attributeName)
        assertReplacement(position, "a>b", 24, 27)
    }

    @Test
    fun lessThanInsideQuotedValue_isCurrentlyTreatedAsTagBoundary() {
        val position = positionAt("<TextView android:text=\"a<bc|")

        assertEquals(XmlCompletionKind.TAG_NAME, position.kind)
        assertEquals("bc", position.tag)
        assertReplacement(position, "bc", 26, 28)
    }

    @Test
    fun unterminatedAttributeValue_atEndOfTag_remainsCompletable() {
        val position = positionAt("<TextView android:text=\"|")

        assertEquals(XmlCompletionKind.ATTRIBUTE_VALUE, position.kind)
        assertEquals("TextView", position.tag)
        assertEquals("android:text", position.attributeName)
        assertEquals(setOf("android:text"), position.existingAttributes)
        assertReplacement(position, "", 24, 24)
    }

    @Test
    fun contributor_tagNamePosition_returnsLayoutTagsInCatalogOrder() {
        val labels = AndroidXmlContributor.contribute(positionAt("<Tex|")).map { it.label }

        assertEquals(
            listOf(
                "LinearLayout", "RelativeLayout", "FrameLayout", "ConstraintLayout",
                "androidx.constraintlayout.widget.ConstraintLayout", "TextView", "Button",
                "ImageView", "EditText", "ScrollView", "RecyclerView", "CardView",
                "androidx.cardview.widget.CardView", "Switch", "CheckBox", "RadioButton",
                "RadioGroup", "ProgressBar", "SeekBar", "Spinner", "Space", "View",
                "ImageButton", "com.google.android.material.button.MaterialButton",
                "com.google.android.material.textfield.TextInputLayout", "include", "merge",
                "fragment",
            ),
            labels,
        )
    }

    @Test
    fun contributor_attributeNamePosition_excludesExistingAttributes() {
        val items = AndroidXmlContributor.contribute(
            positionAt("<TextView android:text=\"Title\" |"),
        )
        val labels = items.map { it.label }

        assertEquals("android:layout_width", labels.first())
        assertTrue("android:layout_height" in labels)
        assertFalse("android:text" in labels)
        assertTrue(items.all { it.typeText == "attribute" })
    }

    @Test
    fun contributor_attributeValuePosition_returnsValuesForAttribute() {
        val labels = AndroidXmlContributor.contribute(
            positionAt("<LinearLayout android:orientation=\"v|"),
        ).map { it.label }

        assertEquals(listOf("vertical", "horizontal"), labels)
    }

    private fun assertUnknown(markedText: String) {
        val position = positionAt(markedText)
        val caret = markedText.indexOf('|')
        assertEquals(XmlCompletionKind.UNKNOWN, position.kind)
        assertReplacement(position, "", caret, caret)
    }

    private fun assertReplacement(
        position: XmlCompletionPosition,
        prefix: String,
        replaceStart: Int,
        replaceEnd: Int,
    ) {
        assertEquals(prefix, position.prefix)
        assertEquals(replaceStart, position.replaceStart)
        assertEquals(replaceEnd, position.replaceEnd)
    }

    private fun positionAt(markedText: String): XmlCompletionPosition {
        val caret = markedText.indexOf('|')
        val text = markedText.removeRange(caret, caret + 1)
        return XmlCompletionAnalyzer.locate(
            text = text,
            offset = caret,
            parsed = XmlParser(text).parse(),
            filePath = "res/layout/screen.xml",
        )
    }
}
