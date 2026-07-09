package com.example.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
class DiagnosticTest {
    @Test
    fun flagsTodoAsWarning() {
        val diagnostics = DemoDiagnostics.analyze("val x = 1 // TODO refactor")
        val warning = diagnostics.single { it.severity == DiagnosticSeverity.Warning }
        assertEquals("TODO", "val x = 1 // TODO refactor".substring(warning.start, warning.end))
    }
    @Test
    fun flagsFooBarAsError() {
        val diagnostics = DemoDiagnostics.analyze("return fooBar")
        assertTrue(diagnostics.any { it.severity == DiagnosticSeverity.Error })
    }
    @Test
    fun doesNotFlagFooBarSubstringInsideAnotherWord() {
        val diagnostics = DemoDiagnostics.analyze("val fooBarBaz = 1")
        assertTrue(diagnostics.none { it.severity == DiagnosticSeverity.Error })
    }
    @Test
    fun cleanCodeHasNoDiagnostics() {
        assertTrue(DemoDiagnostics.analyze("val x = 42").isEmpty())
    }
}
