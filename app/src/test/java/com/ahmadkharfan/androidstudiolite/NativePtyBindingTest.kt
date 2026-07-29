package com.ahmadkharfan.androidstudiolite

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The PTY package name is written down in three places that must agree: the Kotlin `NativePty`
 * object, the JNI function names in `asl_pty.c`, and the R8 keep rule.
 *
 * A mismatch does not fail the build. It fails at runtime with `UnsatisfiedLinkError` the first time
 * a terminal is opened, or — worse — only in a release build if the keep rule stops matching and R8
 * strips the native methods. This test ties the C and keep-rule ends to the same package so a future
 * rename cannot quietly break the binding.
 */
class NativePtyBindingTest {

    private val moduleRoot = File("").absoluteFile
    private val cSource = File(moduleRoot, "src/main/cpp/asl_pty.c")
    private val keepRules = File(moduleRoot, "src/main/keepRules/rules.keep")

    private val nativeMethods = listOf("nativeForkPty", "nativeSetWinSize", "nativeWaitFor", "nativeDestroy")

    @Test
    fun `the keep rule and the jni symbols name the same class`() {
        assertTrue("expected $cSource to exist", cSource.isFile)
        assertTrue("expected $keepRules to exist", keepRules.isFile)

        val keptClass = Regex("""-keepclasseswithmembernames class ([\w.]*NativePty)""")
            .find(keepRules.readText())
            ?.groupValues
            ?.get(1)
        assertTrue("no NativePty keep rule found in $keepRules", keptClass != null)

        val expectedPrefix = "Java_" + keptClass!!.replace(".", "_") + "_"
        val actualPrefixes = Regex("""Java_[\w]*_NativePty_""").findAll(cSource.readText())
            .map { it.value }
            .toSet()

        assertEquals(
            "JNI symbols in asl_pty.c must match the class named by the R8 keep rule",
            setOf(expectedPrefix),
            actualPrefixes,
        )
    }

    @Test
    fun `every declared native method has a jni implementation`() {
        val c = cSource.readText()
        val missing = nativeMethods.filterNot { c.contains("_NativePty_$it") }
        assertEquals("native methods without a JNI implementation", emptyList<String>(), missing)
    }
}
