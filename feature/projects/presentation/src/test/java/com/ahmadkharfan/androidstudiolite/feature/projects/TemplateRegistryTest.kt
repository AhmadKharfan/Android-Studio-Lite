package com.ahmadkharfan.androidstudiolite.feature.projects

import com.ahmadkharfan.androidstudiolite.data.templates.TemplateRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TemplateRegistryTest {

    @Test
    fun `templates are listed in the same order, under the same names`() {
        val expected = listOf(
            "no-activity" to "No Activity",
            "basic-views" to "Basic Activity",
            "empty-views" to "Empty Activity",
            "empty-compose" to "Compose Activity",
            "bottom-nav" to "Bottom Navigation",
            "nav-drawer" to "Navigation drawer",
        )

        val actual = TemplateRegistry.DEFAULT.map { it.metadata.id to it.metadata.name }

        assertEquals(expected, actual)
    }

    @Test
    fun `every template points at thumbnail artwork that exists`() {
        val keys = TemplateRegistry.DEFAULT.map { it.metadata.thumbnail }

        assertEquals("thumbnails must be unique", keys.size, keys.toSet().size)
        for (key in keys) {
            assertTrue("thumbnail key is blank", key.isNotBlank())
            assertTrue("missing drawable: $key", File("src/main/res/drawable/$key.xml").isFile)
        }
    }
}
