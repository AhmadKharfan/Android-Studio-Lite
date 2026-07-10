package com.example.androidstudiolite.feature.editor.engine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinSyntaxChecksTest {
    private fun k(text: String) = LanguageDiagnostics.kotlinHeuristic(text, semantic = false)
    private fun syntaxErrors(text: String) = k(text).filter { it.code == DiagnosticCodes.KT_SYNTAX }

    @Test fun bareValFlagged() {
        val d = syntaxErrors("fun f() {\n    val\n}")
        assertTrue("bare val should be a syntax error, got ${k("fun f() {\n    val\n}")}",
            d.any { it.message == "Expecting a property name" })
    }
    @Test fun bareVarFlagged() {
        assertTrue(syntaxErrors("var").any { it.message == "Expecting a property name" })
    }
    @Test fun bareFunFlagged() {
        assertTrue(syntaxErrors("fun").any { it.message == "Expecting function name" })
    }
    @Test fun bareClassFlagged() {
        assertTrue(syntaxErrors("class").any { it.message == "Name expected" })
    }
    @Test fun bareInterfaceFlagged() {
        assertTrue(syntaxErrors("interface").any { it.message == "Name expected" })
    }
    @Test fun keywordAsNameFlagged() {
        assertTrue(syntaxErrors("val fun x").any { it.message == "Expecting a property name" })
    }
    @Test fun valFollowedByEqualsFlagged() {
        assertTrue(syntaxErrors("val = 5").any { it.message == "Expecting a property name" })
    }

    @Test fun emptyInitializerFlagged() {
        assertTrue(syntaxErrors("fun f() {\n    val x =\n}").any { it.message == "Expecting an expression" })
    }
    @Test fun initializerOnNextLineNotFlagged() {
        assertTrue(syntaxErrors("fun f() {\n    val x =\n        5\n}").none { it.message == "Expecting an expression" })
    }
    @Test fun normalInitializerNotFlagged() {
        assertTrue(syntaxErrors("val x = 1").none { it.message == "Expecting an expression" })
    }
    @Test fun comparisonNotTreatedAsInitializer() {
        assertTrue(syntaxErrors("fun f() {\n    val ok = a == b\n}").none { it.message == "Expecting an expression" })
    }

    @Test fun emptyNamedArgFlagged() {
        assertTrue(syntaxErrors("fun f() {\n    Image(contentDescription =)\n}").any { it.message == "Expecting an expression" })
    }
    @Test fun emptyNamedArgAcrossLinesFlagged() {
        assertTrue(syntaxErrors("fun f() {\n    Image(\n        contentDescription =\n    )\n}").any { it.message == "Expecting an expression" })
    }
    @Test fun filledNamedArgNotFlagged() {
        assertTrue(syntaxErrors("fun f() {\n    Text(text = \"hi\")\n}").none { it.message == "Expecting an expression" })
    }

    @Test fun loneValReportsOnOwnLineNotGluedBelow() {
        val d = syntaxErrors("val\n\nz")
        assertTrue("val alone should be flagged on its line", d.any { it.message == "Expecting a property name" })
        assertTrue("`z` below must not become a property decl", k("val\n\nz").none { it.message.startsWith("This property") })
    }

    @Test fun missingTypeFlagged() {
        assertTrue(syntaxErrors("fun f() {\n    val x:\n}").any { it.message == "Type expected" })
    }

    @Test fun emptyTypeBeforeEqualsFlagged() {
        assertTrue(syntaxErrors("fun f() {\n    val x: = 5\n}").any { it.message == "Type expected" })
    }
    @Test fun emptyParamTypeFlagged() {
        assertTrue(syntaxErrors("fun f(x: ) {}").any { it.message == "Type expected" })
    }

    @Test fun validValNotFlagged() { assertTrue(syntaxErrors("val x = 1").isEmpty()) }
    @Test fun validFunNotFlagged() { assertTrue(syntaxErrors("fun foo() {}").isEmpty()) }
    @Test fun validClassNotFlagged() { assertTrue(syntaxErrors("class Foo").isEmpty()) }
    @Test fun validDestructuringNotFlagged() { assertTrue(syntaxErrors("fun f() {\n    val (a, b) = pair\n}").isEmpty()) }
    @Test fun validGenericFunNotFlagged() { assertTrue(syntaxErrors("fun <T> id(x: T): T = x").isEmpty()) }
    @Test fun validExtensionFunNotFlagged() { assertTrue(syntaxErrors("fun String.shout() = uppercase()").isEmpty()) }
    @Test fun validSupertypeColonNotFlagged() { assertTrue(syntaxErrors("class A : B()").none { it.message == "Type expected" }) }
    @Test fun validTypedValNotFlagged() { assertTrue(syntaxErrors("val x: Int = 1").isEmpty()) }
    @Test fun anonymousFunctionNotFlagged() { assertTrue(syntaxErrors("val f = fun() { }").isEmpty()) }
    @Test fun softKeywordAsNameNotFlagged() {
        assertTrue(syntaxErrors("val data = 1\nvar value = 2").isEmpty())
    }

    @Test fun ifWithoutParenFlagged() {
        assertTrue(syntaxErrors("fun f() {\n    if x\n}").any { it.message == "Expecting '('" })
    }
    @Test fun forWithoutParenFlagged() {
        assertTrue(syntaxErrors("fun f() {\n    for x\n}").any { it.message == "Expecting '('" })
    }
    @Test fun validIfNotFlagged() { assertTrue(syntaxErrors("fun f() {\n    if (a) b()\n}").isEmpty()) }
    @Test fun validForNotFlagged() { assertTrue(syntaxErrors("fun f() {\n    for (x in xs) g(x)\n}").isEmpty()) }
    @Test fun validWhileNotFlagged() { assertTrue(syntaxErrors("fun f() {\n    while (a) b()\n}").isEmpty()) }
    @Test fun ifExpressionNotFlagged() { assertTrue(syntaxErrors("val x = if (a) 1 else 2").isEmpty()) }

    @Test fun bareImportFlagged() {
        assertTrue(syntaxErrors("import").any { it.message == "Expecting qualified name" })
    }
    @Test fun validImportNotFlagged() { assertTrue(syntaxErrors("import a.b.C").isEmpty()) }
    @Test fun validPackageNotFlagged() { assertTrue(syntaxErrors("package com.example.app").isEmpty()) }

    @Test fun danglingPlusFlagged() {
        assertTrue(syntaxErrors("fun f() {\n    val x = a +\n}").any { it.message == "Expecting an expression" })
    }
    @Test fun danglingAndInIfFlagged() {
        assertTrue(syntaxErrors("fun f() {\n    if (a && ) {}\n}").any { it.message == "Expecting an expression" })
    }
    @Test fun danglingElvisFlagged() {
        assertTrue(syntaxErrors("fun f() {\n    val x = a ?:\n}").any { it.message == "Expecting an expression" })
    }
    @Test fun operatorWithOperandNotFlagged() {
        assertTrue(syntaxErrors("val x = a + b").none { it.message == "Expecting an expression" })
    }
    @Test fun operatorAcrossLinesNotFlagged() {
        assertTrue(syntaxErrors("fun f() {\n    val x = a +\n        b\n}").none { it.message == "Expecting an expression" })
    }
    @Test fun spreadNotFlagged() { assertTrue(syntaxErrors("fun f() {\n    foo(*args)\n}").none { it.message == "Expecting an expression" }) }
    @Test fun wildcardImportNotFlagged() { assertTrue(syntaxErrors("import kotlin.collections.*").none { it.message == "Expecting an expression" }) }
    @Test fun starProjectionNotFlagged() { assertTrue(syntaxErrors("val xs: List<*> = emptyList()").none { it.message == "Expecting an expression" }) }
    @Test fun incrementNotFlagged() { assertTrue(syntaxErrors("fun f() {\n    var i = 0\n    i++\n}").none { it.message == "Expecting an expression" }) }
    @Test fun rangeNotFlagged() { assertTrue(syntaxErrors("fun f() {\n    for (i in 0..10) g(i)\n}").none { it.message == "Expecting an expression" }) }
    @Test fun lambdaArrowNotFlagged() { assertTrue(syntaxErrors("val f = { x: Int -> x * 2 }").none { it.message == "Expecting an expression" }) }
    @Test fun comparisonWithOperandNotFlagged() { assertTrue(syntaxErrors("val ok = a >= b").none { it.message == "Expecting an expression" }) }

    @Test fun emptyIfConditionFlagged() {
        assertTrue(syntaxErrors("fun f() {\n    if () {}\n}").any { it.message == "Expecting an expression" })
    }
    @Test fun nonEmptyIfNotFlagged() {
        assertTrue(syntaxErrors("fun f() {\n    if (a) {}\n}").none { it.message == "Expecting an expression" })
    }

    private fun topLevelErrors(text: String) = k(text).filter { it.message == "Expecting a top level declaration" }
    @Test fun bareIdentifierAtTopLevelFlagged() {
        assertTrue(topLevelErrors("class A {}\ng").isNotEmpty())
    }
    @Test fun nullAtTopLevelFlagged() { assertTrue(topLevelErrors("class A {}\nnull").isNotEmpty()) }
    @Test fun builtinNameAtTopLevelFlagged() {
        assertTrue(topLevelErrors("class A {}\nprint").isNotEmpty())
        assertTrue(topLevelErrors("class A {}\ndp").isNotEmpty())
    }
    @Test fun callAtTopLevelFlagged() { assertTrue(topLevelErrors("class A {}\nfoo()").isNotEmpty()) }
    @Test fun numberAtTopLevelFlagged() { assertTrue(topLevelErrors("val x = 5\n42").isNotEmpty()) }
    @Test fun reportedMultilineJunkAllFlagged() {
        val src = "class MainActivity {\n}\ng\n\ngreeting\n\nnull\n\ndp\n\nprint"
        assertEquals(5, topLevelErrors(src).size)
    }

    @Test fun topLevelDeclarationsClean() {
        val src = "package com.example\nimport a.b.C\nval x = 1\nvar y = 2\nfun f() {}\nclass K\nobject O\ninterface I\ntypealias T = Int\nprivate const val Z = 3\n@Deprecated(\"x\")\nfun g() {}"
        assertEquals(emptyList<Diagnostic>(), topLevelErrors(src))
    }
    @Test fun multilinePropertyInitializerNotFlagged() {
        assertTrue(topLevelErrors("val x =\n    computeValue()").isEmpty())
        assertTrue(topLevelErrors("val x = foo\n    .bar()\n    .baz()").isEmpty())
    }
    @Test fun multilineTypeAndSupertypeNotFlagged() {
        assertTrue(topLevelErrors("val x:\n    List<Int> = emptyList()").isEmpty())
        assertTrue(topLevelErrors("class A :\n    Base()").isEmpty())
    }
    @Test fun propertyAccessorOnNextLineNotFlagged() {
        assertTrue(topLevelErrors("val x: Int\n    get() = 5").isEmpty())
        assertTrue(topLevelErrors("var y: Int = 0\n    set(value) { field = value }").isEmpty())
    }
    @Test fun delegatedPropertyNotFlagged() {
        assertTrue(topLevelErrors("val x by lazy { 5 }").isEmpty())
        assertTrue(topLevelErrors("val x\n    by lazy { 5 }").isEmpty())
    }
    @Test fun multilineFunctionSignatureNotFlagged() {
        assertTrue(topLevelErrors("fun f(\n    a: Int,\n    b: Int\n): Int = a + b").isEmpty())
    }
    @Test fun genericWhereClauseNotFlagged() {
        assertTrue(topLevelErrors("fun <T> f(x: T): T\n    where T : Any = x").isEmpty())
    }
    @Test fun infixInitializerSplitNotFlagged() {
        assertTrue(topLevelErrors("val p = 1 to\n    2").isEmpty())
    }
    @Test fun statementsInsideFunctionNotFlagged() {
        assertTrue(topLevelErrors("fun f() {\n    g\n    print\n    dp\n}").isEmpty())
    }
    @Test fun closingBraceLinesNotFlagged() {
        assertTrue(topLevelErrors("class A {\n    fun f() {\n    }\n}").isEmpty())
    }

    @Test fun sampleMainActivityNoSyntaxErrors() {
        val src = "package com.example.myapplication\nimport android.os.Bundle\nclass MainActivity : ComponentActivity() {\n    override fun onCreate(savedInstanceState: Bundle?) {\n        super.onCreate(savedInstanceState)\n        val greeting = \"Hello\"\n    }\n}"
        assertEquals(emptyList<Diagnostic>(), syntaxErrors(src))
    }
}
