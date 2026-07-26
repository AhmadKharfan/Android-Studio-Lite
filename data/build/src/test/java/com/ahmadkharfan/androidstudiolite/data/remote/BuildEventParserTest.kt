package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.data.remote.protocol.BuildEventParser
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildEventParserTest {

    private val parser = BuildEventParser()
    private val root = File("/tmp/project")

    @Test
    fun `parses started with request`() {
        val event = parser.parse(
            """{"type":"started","request":{"buildId":"b1","modulePath":":app","variantName":"debug","kind":"ASSEMBLE"}}""",
            root,
        )
        assertTrue(event is BuildEvent.Started)
        val req = (event as BuildEvent.Started).request
        assertEquals(":app", req.modulePath)
        assertEquals("debug", req.variantName)
        assertEquals(BuildKind.ASSEMBLE, req.kind)
        assertEquals(root, req.projectRoot)
    }

    @Test
    fun `parses progress`() {
        val event = parser.parse("""{"type":"progress","message":"Configuring project ':app'"}""", root)
        assertEquals(BuildEvent.Progress("Configuring project ':app'"), event)
    }

    @Test
    fun `parses taskStarted`() {
        val event = parser.parse("""{"type":"taskStarted","taskPath":":app:compileDebugKotlin"}""", root)
        assertEquals(BuildEvent.TaskStarted(":app:compileDebugKotlin"), event)
    }

    @Test
    fun `parses taskFinished with result enum`() {
        val event = parser.parse(
            """{"type":"taskFinished","taskPath":":app:compileDebugKotlin","result":"UP_TO_DATE"}""",
            root,
        )
        assertEquals(
            BuildEvent.TaskFinished(":app:compileDebugKotlin", BuildEvent.TaskResult.UP_TO_DATE),
            event,
        )
    }

    @Test
    fun `parses output with stream enum`() {
        val event = parser.parse("""{"type":"output","line":"> Task :app:preBuild FAILED","stream":"STDERR"}""", root)
        assertEquals(
            BuildEvent.Output("> Task :app:preBuild FAILED", BuildEvent.OutputStream.STDERR),
            event,
        )
    }

    @Test
    fun `parses problem and resolves file under project root`() {
        val event = parser.parse(
            """{"type":"problem","severity":"ERROR","message":"unresolved reference: foo","file":"app/src/main/java/A.kt","line":12,"column":5}""",
            root,
        )
        assertTrue(event is BuildEvent.Problem)
        val p = event as BuildEvent.Problem
        assertEquals(BuildEvent.ProblemSeverity.ERROR, p.severity)
        assertEquals("unresolved reference: foo", p.message)
        assertEquals(File(root, "app/src/main/java/A.kt"), p.file)
        assertEquals(12, p.line)
        assertEquals(5, p.column)
    }

    @Test
    fun `parses problem with null file`() {
        val event = parser.parse("""{"type":"problem","severity":"WARNING","message":"deprecated"}""", root)
        val p = event as BuildEvent.Problem
        assertNull(p.file)
        assertNull(p.line)
        assertEquals(BuildEvent.ProblemSeverity.WARNING, p.severity)
    }

    @Test
    fun `parses artifactProduced`() {
        val event = parser.parse("""{"type":"artifactProduced","name":"app-debug.apk","kind":"APK"}""", root)
        assertTrue(event is BuildEvent.ArtifactProduced)
        val a = event as BuildEvent.ArtifactProduced
        assertEquals("app-debug.apk", a.file.name)
        assertEquals(BuildEvent.ArtifactKind.APK, a.kind)
    }

    @Test
    fun `parses finished`() {
        val event = parser.parse("""{"type":"finished","success":true,"durationMillis":48213}""", root)
        assertEquals(BuildEvent.Finished(success = true, durationMillis = 48213), event)
    }

    @Test
    fun `ignores unknown type`() {
        assertNull(parser.parse("""{"type":"heartbeat","ts":123}""", root))
    }

    @Test
    fun `ignores unknown fields on a known type`() {
        val event = parser.parse("""{"type":"progress","message":"x","extra":"ignored","nested":{"a":1}}""", root)
        assertEquals(BuildEvent.Progress("x"), event)
    }

    @Test
    fun `unknown enum value falls back to safe default`() {
        val event = parser.parse("""{"type":"taskFinished","taskPath":":app:foo","result":"WHAT_IS_THIS"}""", root)
        assertEquals(BuildEvent.TaskResult.SUCCESS, (event as BuildEvent.TaskFinished).result)
    }

    @Test
    fun `returns null for malformed and blank frames`() {
        assertNull(parser.parse("not json", root))
        assertNull(parser.parse("", root))
        assertNull(parser.parse("   ", root))
    }

    @Test
    fun `parseNdjson skips unparseable lines and keeps order`() {
        val block = """
            {"type":"started","request":{"modulePath":":app","variantName":"debug","kind":"ASSEMBLE"}}
            garbage
            {"type":"taskStarted","taskPath":":app:preBuild"}
            {"type":"finished","success":false,"durationMillis":10}
        """.trimIndent()
        val events = parser.parseNdjson(block, root)
        assertEquals(3, events.size)
        assertTrue(events[0] is BuildEvent.Started)
        assertTrue(events[1] is BuildEvent.TaskStarted)
        assertEquals(BuildEvent.Finished(false, 10), events[2])
    }
}
