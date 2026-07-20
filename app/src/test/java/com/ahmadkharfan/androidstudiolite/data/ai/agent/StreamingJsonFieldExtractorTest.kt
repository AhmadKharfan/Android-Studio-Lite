package com.ahmadkharfan.androidstudiolite.data.ai.agent

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamingJsonFieldExtractorTest {

    @Test
    fun `skips markdown fence before json`() {
        val thought = StringBuilder()
        val final = StringBuilder()
        val extractor = StreamingJsonFieldExtractor(
            onThought = { thought.append(it) },
            onFinal = { final.append(it) },
        )
        extractor.feed("```json\n{\"thought\":\"Reading\",\"actions\":[]}\n```")
        assertEquals("Reading", thought.toString())
        assertEquals("", final.toString())
    }

    @Test
    fun `extracts thought and final from chunked json`() {
        val thought = StringBuilder()
        val final = StringBuilder()
        val extractor = StreamingJsonFieldExtractor(
            onThought = { thought.append(it) },
            onFinal = { final.append(it) },
        )
        val json = """{"thought":"Reading files","actions":[{"tool":"read_file","path":"a.kt"}],"final":"Done"}"""

        json.chunked(3).forEach { extractor.feed(it) }
        assertEquals("Reading files", thought.toString())
        assertEquals("Done", final.toString())
    }

    @Test
    fun `decodes escape sequences`() {
        val thought = StringBuilder()
        val extractor = StreamingJsonFieldExtractor(
            onThought = { thought.append(it) },
            onFinal = {},
        )
        extractor.feed("""{"thought":"line1\nline2 \"quoted\""}""")
        assertEquals("line1\nline2 \"quoted\"", thought.toString())
    }

    @Test
    fun `prose without json streams as final`() {
        val final = StringBuilder()
        val extractor = StreamingJsonFieldExtractor(
            onThought = {},
            onFinal = { final.append(it) },
        )
        extractor.feed("Hello ")
        extractor.feed("world")
        assertEquals("Hello world", final.toString())
    }

    @Test
    fun `ignores nested thought keys`() {
        val thought = StringBuilder()
        val final = StringBuilder()
        val extractor = StreamingJsonFieldExtractor(
            onThought = { thought.append(it) },
            onFinal = { final.append(it) },
        )
        extractor.feed("""{"thought":"top","actions":[{"tool":"x","thought":"nested"}],"final":"ok"}""")
        assertEquals("top", thought.toString())
        assertEquals("ok", final.toString())
    }
}
