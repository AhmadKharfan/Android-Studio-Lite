package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.Project
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectRecordCodecTest {

    @Test
    fun `encode preserves the persisted field order and record delimiters`() {
        val projects = listOf(
            Project("alpha", "Alpha", "/projects/alpha", "Kotlin", 123L, "com.example.alpha", true),
            Project("beta", "Beta", "/projects/beta", "Java", 456L, "com.example.beta", false),
        )

        val encoded = ProjectRecordCodec.encode(projects)

        assertEquals(
            "alpha\tAlpha\t/projects/alpha\tKotlin\t123\tcom.example.alpha\ttrue\n" +
                "beta\tBeta\t/projects/beta\tJava\t456\tcom.example.beta\tfalse",
            encoded,
        )
    }

    @Test
    fun `null timestamp and blank package retain their legacy null representation`() {
        val project = Project("id", "Name", "/project", "Java", null, "", true)

        val encoded = ProjectRecordCodec.encode(listOf(project))
        val decoded = ProjectRecordCodec.decode(encoded).single()

        assertEquals("id\tName\t/project\tJava\t0\t\ttrue", encoded)
        assertNull(decoded.lastOpenedMillis)
        assertNull(decoded.packageName)
        assertNull(ProjectRecordCodec.decode("id\tName\t/project\tJava\t-1\t\ttrue").single().lastOpenedMillis)
    }

    @Test
    fun `delimiter characters are escaped without changing their decoded values`() {
        val project = Project(
            id = "id\\part",
            name = "tab\tname",
            path = "/line\npath",
            language = "Kot\\lin\tX\nY",
            lastOpenedMillis = 42L,
            packageName = "pkg\\\t\n",
            buildable = false,
        )

        val encoded = ProjectRecordCodec.encode(listOf(project))

        assertEquals(
            "id\\\\part\ttab\\tname\t/line\\npath\tKot\\\\lin\\tX\\nY\t42\tpkg\\\\\\t\\n\tfalse",
            encoded,
        )
        assertEquals(listOf(project), ProjectRecordCodec.decode(encoded))
    }

    @Test
    fun `legacy five-field records decode and shorter records are dropped`() {
        val decoded = ProjectRecordCodec.decode(
            "legacy\tLegacy\t/projects/legacy\tKotlin\t123\nshort\tShort\t/path\tJava",
        )

        assertEquals(1, decoded.size)
        assertNull(decoded.single().packageName)
        assertTrue(decoded.single().buildable)
    }

    @Test
    fun `empty stored text decodes to no projects`() {
        assertTrue(ProjectRecordCodec.decode("").isEmpty())
    }
}
