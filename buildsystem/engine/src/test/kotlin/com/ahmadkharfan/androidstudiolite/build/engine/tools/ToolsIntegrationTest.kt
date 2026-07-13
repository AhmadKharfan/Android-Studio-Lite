package com.ahmadkharfan.androidstudiolite.build.engine.tools

import com.android.apksig.ApkVerifier
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipFile
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Exercises the real tail of the pipeline in-process on the JVM: ECJ compiles Java → D8 dexes it →
 * [ApkPackager] zips it → apksig signs it → [ApkVerifier] confirms the signature. aapt2 and kotlinc
 * are excluded here (native binary / android platform needed); the pipeline orchestration is covered
 * separately with fakes in `BuildPipelineTest`.
 */
class ToolsIntegrationTest {

    private lateinit var work: File

    @Before fun setup() {
        work = Files.createTempDirectory("tools").toFile()
    }

    @Test fun `compile, dex, package and sign produces a verifiable APK`() {
        // 1. A trivial Java source (only java.lang, so no android.jar is needed).
        val src = File(work, "src").apply { mkdirs() }
        File(src, "Hello.java").writeText(
            """
            package demo;
            public final class Hello {
                public static String greet(String who) { return "Hello, " + who; }
            }
            """.trimIndent(),
        )

        // 2. ECJ compile.
        val classes = File(work, "classes")
        val compile = EcjJavaCompiler().compile(
            JavaCompileRequest(
                sourceFiles = emptyList(),
                sourceRoots = listOf(src),
                classpath = emptyList(),
                bootClasspath = emptyList(), // ECJ falls back to the running JDK's platform classes
                outputDir = classes,
            ),
        )
        assertTrue("ECJ output:\n${compile.rawOutput}", compile.success)
        assertTrue(File(classes, "demo/Hello.class").isFile)

        // 3. D8 dex.
        val dexDir = File(work, "dex")
        D8Dexer().dex(
            DexRequest(
                programFiles = listOf(classes),
                libraryFiles = emptyList(),
                minApiLevel = 24,
                output = dexDir,
            ),
        )
        assertTrue("classes.dex missing", File(dexDir, "classes.dex").isFile)

        // 4. Package an unsigned APK (raw manifest is fine for a signing round-trip).
        val manifest = File(work, "AndroidManifest.xml").apply { writeText("<manifest/>") }
        val unsigned = File(work, "unsigned.apk")
        ApkPackager().buildUnsignedApk(
            baseApk = null,
            dexFiles = listOf(File(dexDir, "classes.dex")),
            output = unsigned,
            rawManifest = manifest,
        )
        ZipFile(unsigned).use { assertTrue(it.getEntry("classes.dex") != null) }

        // 5. Sign with an auto-generated debug keystore.
        val key = DebugKeystore(File(work, "debug.keystore")).load()
        val signed = File(work, "signed.apk")
        Apksigner().sign(unsigned, signed, key, minSdkVersion = 24)

        // 6. apksig confirms the signature is valid.
        val verdict = ApkVerifier.Builder(signed).setMinCheckedPlatformVersion(24).build().verify()
        assertTrue("apksig errors: ${verdict.errors}", verdict.isVerified)
    }

    @Test fun `debug keystore is stable across reloads`() {
        val file = File(work, "debug.keystore")
        val first = DebugKeystore(file).load()
        val second = DebugKeystore(file).load()
        // Same persisted key/cert on reload (keystore generated once).
        assertTrue(first.certificateChain.first() == second.certificateChain.first())
    }
}
