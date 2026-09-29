package com.ahmadkharfan.androidstudiolite.data.githubactions.logs

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradleProblemParserTest {

    private val root = File("/device/projects/MyApp")
    private val parser = GradleProblemParser(root, workspace = "/home/runner/work/asl-build/asl-build")

    @Test
    fun `kotlin 2 errors point at the local file, line and column`() {
        val problem = parser.parse(
            "e: file:///home/runner/work/asl-build/asl-build/app/src/main/java/com/example/MainActivity.kt:20:9 Unresolved reference 'foo'.",
        )

        assertEquals(
            BuildEvent.Problem(
                BuildEvent.ProblemSeverity.ERROR,
                "Unresolved reference 'foo'.",
                File(root, "app/src/main/java/com/example/MainActivity.kt"),
                20,
                9,
            ),
            problem,
        )
    }

    @Test
    fun `kotlin warnings are warnings`() {
        val problem = parser.parse("w: file:///home/runner/work/asl-build/asl-build/app/src/A.kt:3:5 'x' is deprecated.")

        assertEquals(BuildEvent.ProblemSeverity.WARNING, problem?.severity)
    }

    @Test
    fun `older kotlin output is understood`() {
        val problem = parser.parse("e: /home/runner/work/asl-build/asl-build/app/src/A.kt: (7, 13): Unresolved reference: bar")

        assertEquals(File(root, "app/src/A.kt"), problem?.file)
        assertEquals(7, problem?.line)
        assertEquals(13, problem?.column)
        assertEquals("Unresolved reference: bar", problem?.message)
    }

    @Test
    fun `javac errors are understood`() {
        val problem = parser.parse("/home/runner/work/asl-build/asl-build/app/src/main/java/Foo.java:12: error: cannot find symbol")

        assertEquals(BuildEvent.ProblemSeverity.ERROR, problem?.severity)
        assertEquals(File(root, "app/src/main/java/Foo.java"), problem?.file)
        assertEquals(12, problem?.line)
        assertEquals("cannot find symbol", problem?.message)
    }

    @Test
    fun `aapt resource errors are understood`() {
        val problem = parser.parse(
            "ERROR: /home/runner/work/asl-build/asl-build/app/src/main/res/layout/main.xml:5: AAPT: error: attribute android:foo not found.",
        )

        assertEquals(File(root, "app/src/main/res/layout/main.xml"), problem?.file)
        assertEquals(5, problem?.line)
        assertEquals("attribute android:foo not found.", problem?.message)
    }

    @Test
    fun `problems outside the project keep their message but no file`() {
        val problem = parser.parse("e: file:///home/runner/.gradle/caches/x/Generated.kt:1:1 Something broke")

        assertEquals("Something broke", problem?.message)
        assertNull(problem?.file)
    }

    @Test
    fun `ordinary output is not a problem`() {
        listOf(
            "> Task :app:compileDebugKotlin",
            "BUILD SUCCESSFUL in 1m 2s",
            "e: something without a location",
            "Execution failed for task ':app:compileDebugKotlin'.",
        ).forEach { assertNull(it, parser.parse(it)) }
    }
}
