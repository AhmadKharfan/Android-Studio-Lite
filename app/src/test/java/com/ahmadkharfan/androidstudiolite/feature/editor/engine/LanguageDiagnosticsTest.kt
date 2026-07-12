package com.ahmadkharfan.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class LanguageDiagnosticsTest {
    private fun kotlin(text: String) = LanguageDiagnostics.kotlinHeuristic(text)

    @Test
    fun cleanKotlinHasNoDiagnostics() {
        assertTrue(kotlin("fun main() {\n    println(\"hi\")\n}").isEmpty())
    }

    @Test
    fun flagsUnclosedBrace() {
        val d = kotlin("fun main() {\n    println(\"hi\")\n")
        assertTrue(d.any { it.severity == DiagnosticSeverity.Error && it.code == DiagnosticCodes.KT_SYNTAX })
    }

    @Test
    fun flagsUnexpectedClosingParen() {
        val d = kotlin("val x = 1)")
        val err = d.single { it.code == DiagnosticCodes.KT_SYNTAX }
        assertEquals(DiagnosticSeverity.Error, err.severity)
    }

    @Test
    fun flagsMismatchedBracket() {
        val d = kotlin("val x = foo(1]")
        assertTrue(d.any { it.message.contains("mismatched") })
    }

    @Test
    fun ignoresBracketsInsideStrings() {
        assertTrue(kotlin("val s = \"a ) b } c ]\"").none { it.code == DiagnosticCodes.KT_SYNTAX })
    }

    @Test
    fun ignoresBracketsInsideComments() {
        assertTrue(kotlin("// a ) b } here\nval x = 1").none { it.code == DiagnosticCodes.KT_SYNTAX })
    }

    @Test
    fun ignoresBracketsInRawStringAndTemplateBraces() {
        assertTrue(kotlin("val s = \"\"\"a ) } ]\"\"\"").none { it.code == DiagnosticCodes.KT_SYNTAX })
    }

    @Test
    fun flagsUnterminatedString() {
        val d = kotlin("val s = \"oops")
        assertTrue(d.any { it.code == DiagnosticCodes.KT_SYNTAX })
    }


    @Test
    fun flagsUnusedImportAsMuted() {
        val d = kotlin("import a.b.Unused\n\nfun main() {}")
        val imp = d.single { it.code == DiagnosticCodes.KT_UNUSED_IMPORT }
        assertTrue(imp.muted)
        assertEquals(DiagnosticSeverity.Warning, imp.severity)
        assertEquals("Unused import directive", imp.message)
    }

    @Test
    fun usedImportIsNotFlagged() {
        val d = kotlin("import a.b.Widget\n\nfun main() { Widget() }")
        assertTrue(d.none { it.code == DiagnosticCodes.KT_UNUSED_IMPORT })
    }

    @Test
    fun wildcardImportNeverFlaggedUnused() {
        assertTrue(kotlin("import a.b.*\n\nfun main() {}").none { it.code == DiagnosticCodes.KT_UNUSED_IMPORT })
    }


    @Test
    fun flagsUnresolvedReference() {
        val d = kotlin("fun demo() {\n    Greeting(name)\n}")
        val err = d.first { it.code == DiagnosticCodes.KT_UNRESOLVED }
        assertEquals(DiagnosticSeverity.Error, err.severity)
        assertEquals("Unresolved reference: Greeting", err.message)
    }

    @Test
    fun resolvesDeclaredAndImportedNames() {
        val src = "import a.b.Widget\nfun demo() {\n    val w = Widget()\n    render(w)\n}\nfun render(x: Widget) {}"
        assertTrue(kotlin(src).none { it.code == DiagnosticCodes.KT_UNRESOLVED })
    }

    @Test
    fun skipsDottedMemberAccess() {
        val d = kotlin("fun demo(obj: Thing) {\n    obj.unknownMember()\n}")
        assertTrue(d.none { it.code == DiagnosticCodes.KT_UNRESOLVED && it.message.contains("unknownMember") })
    }

    @Test
    fun semanticOffWhenDisabled() {
        val d = LanguageDiagnostics.kotlinHeuristic("fun demo() { Greeting(x) }", semantic = false)
        assertTrue(d.none { it.code == DiagnosticCodes.KT_UNRESOLVED })
    }

    @Test
    fun wildcardImportSuppressesUnresolved() {
        val d = kotlin("import a.b.*\nfun demo() {\n    Greeting(x)\n}")
        assertTrue(d.none { it.code == DiagnosticCodes.KT_UNRESOLVED })
    }

    @Test
    fun doesNotFlagClassMembersOrTopLevel() {
        val src = "import x.MutableStateFlow\nclass VM {\n    private val _count = MutableStateFlow(0)\n    val count = _count\n}"
        assertTrue(kotlin(src).none { it.severity == DiagnosticSeverity.Error })
    }

    @Test
    fun sampleMainActivityOnlyFlagsGreeting() {
        val src = """
            package com.example.myapplication
            import android.os.Bundle
            import androidx.activity.ComponentActivity
            import androidx.activity.compose.setContent
            class MainActivity : ComponentActivity() {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    val greeting = "Hello, Lite!"
                    setContent {
                        Greeting(greeting)
                    }
                }
            }
        """.trimIndent()
        val d = kotlin(src)
        assertEquals(1, d.size)
        assertEquals("Unresolved reference: Greeting", d.single().message)
    }

    @Test
    fun sampleMainViewModelIsClean() {
        val src = """
            package com.example.myapplication
            import androidx.lifecycle.ViewModel
            import kotlinx.coroutines.flow.MutableStateFlow
            import kotlinx.coroutines.flow.asStateFlow
            class MainViewModel : ViewModel() {
                private val _count = MutableStateFlow(0)
                val count = _count.asStateFlow()
                fun increment() {
                    _count.value += 1
                }
            }
        """.trimIndent()
        assertTrue(kotlin(src).isEmpty())
    }
}
