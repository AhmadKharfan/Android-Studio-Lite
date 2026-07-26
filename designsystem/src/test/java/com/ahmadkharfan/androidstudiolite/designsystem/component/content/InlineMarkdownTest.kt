package com.ahmadkharfan.androidstudiolite.designsystem.component.content

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import org.junit.Assert.assertEquals
import org.junit.Test

class InlineMarkdownTest {

    @Test
    fun `inline syntax produces current text styles and link annotation`() {
        val textColor = Color(0xFF112233)
        val linkColor = Color(0xFF445566)
        val codeBackground = Color(0xFF778899)

        val annotated = buildInlineAnnotatedString(
            "plain **bold** *ital* `code` [site](https://example.com) end",
            textColor,
            linkColor,
            codeBackground,
        )

        assertEquals("plain bold ital code site end", annotated.text)
        assertEquals(4, annotated.spanStyles.size)
        assertEquals(6 until 10, annotated.spanStyles[0].start until annotated.spanStyles[0].end)
        assertEquals(FontWeight.Bold, annotated.spanStyles[0].item.fontWeight)
        assertEquals(textColor, annotated.spanStyles[0].item.color)
        assertEquals(11 until 15, annotated.spanStyles[1].start until annotated.spanStyles[1].end)
        assertEquals(FontStyle.Italic, annotated.spanStyles[1].item.fontStyle)
        assertEquals(16 until 20, annotated.spanStyles[2].start until annotated.spanStyles[2].end)
        assertEquals(FontFamily.Monospace, annotated.spanStyles[2].item.fontFamily)
        assertEquals(codeBackground, annotated.spanStyles[2].item.background)
        assertEquals(21 until 25, annotated.spanStyles[3].start until annotated.spanStyles[3].end)
        assertEquals(linkColor, annotated.spanStyles[3].item.color)
        assertEquals(TextDecoration.Underline, annotated.spanStyles[3].item.textDecoration)
        val link = annotated.getLinkAnnotations(0, annotated.length).single()
        assertEquals(21 until 25, link.start until link.end)
        assertEquals("https://example.com", (link.item as LinkAnnotation.Url).url)
    }

    @Test
    fun `unclosed inline markers remain literal text`() {
        val source = "before **bold and `code and [site](https://example.com"

        val annotated = buildInlineAnnotatedString(source, Color.Black, Color.Blue, Color.Gray)

        assertEquals(source, annotated.text)
        assertEquals(emptyList<Any>(), annotated.spanStyles)
        assertEquals(emptyList<Any>(), annotated.getLinkAnnotations(0, annotated.length))
    }
}
