package com.ahmadkharfan.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinParseChecksTest {
    private fun k(text: String) = LanguageDiagnostics.analyze(text, EditorLanguage.Kotlin, semantic = false)
    private fun one(text: String, code: String) = k(text).single { it.code == code }

    @Test fun valReassignFlagged() {
        val d = one("fun f() {\n    val x = 1\n    x = 2\n}", DiagnosticCodes.KT_VAL_REASSIGN)
        assertEquals(DiagnosticSeverity.Error, d.severity)
        assertEquals("Val cannot be reassigned", d.message)
    }
    @Test fun varReassignNotFlagged() {
        assertTrue(k("fun f() {\n    var x = 1\n    x = 2\n}").none { it.code == DiagnosticCodes.KT_VAL_REASSIGN })
    }
    @Test fun valEqualityNotReassign() {
        assertTrue(k("fun f() {\n    val x = 1\n    if (x == 2) {}\n}").none { it.code == DiagnosticCodes.KT_VAL_REASSIGN })
    }

    @Test fun lateinitOnValFlagged() {
        assertEquals("'lateinit' modifier is allowed only on mutable properties",
            one("class C { lateinit val x: String }", DiagnosticCodes.KT_LATEINIT).message)
    }
    @Test fun lateinitWithInitializerFlagged() {
        assertEquals("'lateinit' modifier is not allowed on properties with an initializer",
            one("class C { lateinit var x: String = \"a\" }", DiagnosticCodes.KT_LATEINIT).message)
    }
    @Test fun lateinitNullableFlagged() {
        assertEquals("'lateinit' modifier is not allowed on properties of nullable types",
            one("class C { lateinit var x: String? }", DiagnosticCodes.KT_LATEINIT).message)
    }
    @Test fun lateinitNoTypeFlagged() {
        assertEquals("'lateinit' modifier requires the property to have an explicit type",
            one("class C { lateinit var x }", DiagnosticCodes.KT_LATEINIT).message)
    }
    @Test fun validLateinitNotFlagged() {
        assertTrue(k("class C { lateinit var x: String }").none { it.code == DiagnosticCodes.KT_LATEINIT })
    }

    @Test fun topLevelPropertyMustBeInitialized() {
        assertEquals("Property must be initialized",
            one("val x: Int", DiagnosticCodes.KT_MUST_BE_INITIALIZED).message)
    }
    @Test fun interfacePropertyNotFlagged() {
        assertTrue(k("interface I { val x: Int }").none { it.code == DiagnosticCodes.KT_MUST_BE_INITIALIZED })
    }
    @Test fun initializedPropertyNotFlagged() {
        assertTrue(k("class C { val x: Int = 1 }").none { it.code == DiagnosticCodes.KT_MUST_BE_INITIALIZED })
    }
    @Test fun noTypeNoInitializerMember() {
        assertEquals("This property must either have a type annotation, be initialized or be delegated",
            one("class C { val x }", DiagnosticCodes.KT_NO_TYPE_NO_INITIALIZER).message)
    }
    @Test fun noTypeNoInitializerLocal() {
        assertEquals("This variable must either have a type annotation or be initialized",
            one("fun f() { val x }", DiagnosticCodes.KT_NO_TYPE_NO_INITIALIZER).message)
    }

    @Test fun redundantNotNullOnLiteral() {
        assertEquals("Redundant non-null assertion ('!!') on a non-null value",
            one("fun f() = \"x\"!!", DiagnosticCodes.KT_REDUNDANT_NOT_NULL).message)
    }
    @Test fun redundantNotNullOnThis() {
        assertTrue(k("class C { fun f() { this!! } }").any { it.code == DiagnosticCodes.KT_REDUNDANT_NOT_NULL })
    }
    @Test fun redundantSafeCallOnThis() {
        assertEquals("Redundant safe call ('?.') on a non-null receiver",
            one("class C { fun f() { this?.hashCode() } }", DiagnosticCodes.KT_REDUNDANT_SAFE_CALL).message)
    }
    @Test fun notNullOnNameNotFlagged() {
        assertTrue(k("fun f(a: String?) { a!!.length }").none { it.code == DiagnosticCodes.KT_REDUNDANT_NOT_NULL })
    }

    @Test fun unusedParameterFlagged() {
        val d = one("fun f(name: String) {\n    println(\"hi\")\n}", DiagnosticCodes.KT_UNUSED_PARAMETER)
        assertEquals(DiagnosticSeverity.Warning, d.severity)
        assertEquals("Parameter 'name' is never used", d.message)
    }
    @Test fun usedParameterNotFlagged() {
        assertTrue(k("fun f(name: String) {\n    println(name)\n}").none { it.code == DiagnosticCodes.KT_UNUSED_PARAMETER })
    }
    @Test fun overrideParameterExempt() {
        assertTrue(k("override fun f(name: String) {\n    println(\"hi\")\n}").none { it.code == DiagnosticCodes.KT_UNUSED_PARAMETER })
    }
    @Test fun underscoreParameterNotFlagged() {
        assertTrue(k("fun f(_: String) {\n    println(\"hi\")\n}").none { it.code == DiagnosticCodes.KT_UNUSED_PARAMETER })
    }
}
