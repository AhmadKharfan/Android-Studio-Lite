package com.ahmadkharfan.androidstudiolite.data.gradle

import com.ahmadkharfan.androidstudiolite.data.gradle.parse.GTokenType
import com.ahmadkharfan.androidstudiolite.data.gradle.parse.GradleScriptScanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class GradleScriptScannerTest {

    @Test
    fun bracesInsideStringsAndCommentsAreIgnored() {
        val text = """
            android {
                // a stray } brace in a comment
                namespace = "a } b { c"
                defaultConfig { minSdk = 24 }
            }
        """.trimIndent()
        val tokens = GradleScriptScanner.tokenize(text)
        val body = GradleScriptScanner.findBlockBody(tokens, "android")
        assertNotNull(body)
        requireNotNull(body)

        val nested = GradleScriptScanner.findBlockBody(tokens, "defaultConfig", body.first, body.last + 1)
        assertNotNull(nested)
    }

    @Test
    fun tripleQuotedStringsAreSingleTokens() {
        val text = "x = \"\"\"a { b } c\"\"\"\ny = 1"
        val tokens = GradleScriptScanner.tokenize(text)
        val strings = tokens.filter { it.type == GTokenType.STRING }
        assertEquals(1, strings.size)
        assertEquals("a { b } c", strings.first().stringValue())
    }

    @Test
    fun trailingLambdaBlockAfterArgsIsFound() {
        val text = "sourceSets.getByName(\"main\") { java.srcDir(\"src\") }\nandroid { compileSdk = 34 }"
        val tokens = GradleScriptScanner.tokenize(text)
        val body = GradleScriptScanner.findBlockBody(tokens, "android")
        assertNotNull(body)
    }

    @Test
    fun unbalancedBracesReturnNullNotCrash() {
        val tokens = GradleScriptScanner.tokenize("android { compileSdk = 34")
        assertEquals(null, GradleScriptScanner.findBlockBody(tokens, "android"))
    }
}
