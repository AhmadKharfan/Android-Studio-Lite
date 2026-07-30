import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApiContractRulesTest {

    private fun violations(source: String) = findApiContractViolations(mapOf("Api.kt" to source))

    @Test
    fun `composable interface member with a default argument is a violation`() {
        val found = violations(
            """
            interface PanelApi {
                @Composable
                fun Panel(id: String, onClose: () -> Unit = {})
            }
            """.trimIndent(),
        )
        assertEquals(1, found.size)
        assertTrue(found.single().declaration.contains("Panel"))
    }

    @Test
    fun `the real regression is caught across multiple lines`() {
        // The exact shape that shipped an AbstractMethodError.
        val found = violations(
            """
            interface GitPanelApi {
                @Composable
                fun Panel(
                    projectId: String,
                    onClose: () -> Unit,
                    onOpenDiff: (String, GitDiffTarget) -> Unit = { _, _ -> },
                    onOpenHistory: () -> Unit = {},
                )
            }
            """.trimIndent(),
        )
        assertEquals(1, found.size)
    }

    @Test
    fun `composable interface member without defaults is fine`() {
        val found = violations(
            """
            interface PanelApi {
                @Composable
                fun Panel(
                    projectId: String,
                    onClose: () -> Unit,
                    onOpenDiff: (String, GitDiffTarget) -> Unit,
                )
            }
            """.trimIndent(),
        )
        assertTrue(found.isEmpty())
    }

    // ---- cases that must NOT be reported --------------------------------------------------

    @Test
    fun `a non-composable interface member may keep its defaults`() {
        // Plain interface methods do not go through the Compose defaults bridge, so they are safe.
        val found = violations(
            """
            interface BuildRunApi {
                suspend fun clear(buildId: String? = null)
            }
            """.trimIndent(),
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun `a composable that is not an interface member may keep its defaults`() {
        val found = violations(
            """
            @Composable
            fun Standalone(text: String, modifier: Modifier = Modifier) { }
            """.trimIndent(),
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun `a data class default value is not mistaken for one`() {
        val found = violations(
            """
            data class BuildClientMeta(
                val projectId: String,
                val autoLaunchAfterInstall: Boolean = true,
            )
            """.trimIndent(),
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun `a class implementing the interface may keep its overrides`() {
        val found = violations(
            """
            class PanelApiImpl : PanelApi {
                @Composable
                override fun Panel(id: String, onClose: () -> Unit) { }
            }
            """.trimIndent(),
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun `comparison operators in a default expression are not read as defaults`() {
        val found = violations(
            """
            interface PanelApi {
                @Composable
                fun Panel(id: String, onClose: () -> Unit)
                fun compare(a: Int, b: Int): Boolean
            }
            """.trimIndent(),
        )
        assertTrue(found.isEmpty())
    }

    @Test
    fun `each offending declaration is reported once with its file and line`() {
        val found = findApiContractViolations(
            mapOf(
                "A.kt" to "interface A {\n    @Composable\n    fun X(a: Int = 1)\n}",
                "B.kt" to "interface B {\n    @Composable\n    fun Y(b: Int)\n}",
            ),
        )
        assertEquals(1, found.size)
        assertEquals("A.kt", found.single().file)
        assertEquals(3, found.single().line)
    }
}
