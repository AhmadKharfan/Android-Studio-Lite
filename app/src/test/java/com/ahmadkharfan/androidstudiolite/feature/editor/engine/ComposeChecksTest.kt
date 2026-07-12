package com.ahmadkharfan.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertTrue
import org.junit.Test

class ComposeChecksTest {
    private fun k(text: String) = LanguageDiagnostics.kotlinHeuristic(text, semantic = false)
    private fun composeWarnings(text: String) = k(text).filter { it.code == "compose.composableNaming" }

    @Test fun lowercaseComposableFlagged() {
        val d = composeWarnings("@Composable\nfun greeting() {\n    Text(\"hi\")\n}")
        assertTrue("lowercase @Composable Unit fn should warn, got ${k("@Composable\nfun greeting() {}")}",
            d.any { it.message.contains("uppercase") })
    }

    @Test fun uppercaseComposableNotFlagged() {
        assertTrue(composeWarnings("@Composable\nfun Greeting() {\n    Text(\"hi\")\n}").isEmpty())
    }

    @Test fun valueReturningComposableNotFlagged() {
        assertTrue(composeWarnings("@Composable\nfun rememberThing(): Thing = remember { Thing() }").isEmpty())
    }

    @Test fun nonComposableNotFlagged() {
        assertTrue(composeWarnings("fun greeting() {\n    println(\"hi\")\n}").isEmpty())
    }

    @Test fun composableOnSameLineFlagged() {
        assertTrue(composeWarnings("@Composable fun myScreen() {}").any { it.message.contains("uppercase") })
    }
}
