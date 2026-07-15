package com.ahmadkharfan.androidstudiolite.data.templates

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The picker deliberately mirrors android-code-studio's template list — same names, same order — so
 * a user moving between the two IDEs finds each template where they expect it. These are pinned here
 * because they're a cross-app agreement, not an arbitrary UI choice: a rename or reorder is a
 * deliberate decision, not a drive-by edit.
 */
class TemplateRegistryTest {

    @Test
    fun `templates are listed in the same order, under the same names`() {
        val expected = listOf(
            "no-activity" to "No Activity",
            "empty-views" to "Empty project",
            "native-cpp" to "C++ project",
            "basic-views" to "Basic Project",
            "nav-drawer" to "Navigation drawer project",
            "bottom-nav" to "Bottom Navigation Activity",
            "tabbed" to "Tabbed Activity",
            "no-androidx" to "No AndroidX",
            "empty-compose" to "Jetpack Compose",
        )

        val actual = TemplateRegistry.DEFAULT.map { it.metadata.id to it.metadata.name }

        assertEquals(expected, actual)
    }

    /**
     * Every template needs its own artwork on disk. Four templates used to name icons the icon set
     * didn't contain ("layout-dashboard", "package-minus", "layout-grid", "table-columns") and
     * silently rendered a blank fallback, which is exactly the failure this catches.
     */
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
