package com.ahmadkharfan.androidstudiolite.build.engine.pipeline

import com.ahmadkharfan.androidstudiolite.build.common.BuildReporter
import com.ahmadkharfan.androidstudiolite.build.common.CancellationToken
import com.ahmadkharfan.androidstudiolite.build.common.TaskOutcome
import com.ahmadkharfan.androidstudiolite.build.engine.tools.Aapt2LinkRequest
import com.ahmadkharfan.androidstudiolite.build.engine.tools.Aapt2Result
import com.ahmadkharfan.androidstudiolite.build.engine.tools.Aapt2Tool
import com.ahmadkharfan.androidstudiolite.build.engine.tools.ApkPackager
import com.ahmadkharfan.androidstudiolite.build.engine.tools.ApkSignerTool
import com.ahmadkharfan.androidstudiolite.build.engine.tools.CompileResult
import com.ahmadkharfan.androidstudiolite.build.engine.tools.DexRequest
import com.ahmadkharfan.androidstudiolite.build.engine.tools.DexTool
import com.ahmadkharfan.androidstudiolite.build.engine.tools.JavaCompileRequest
import com.ahmadkharfan.androidstudiolite.build.engine.tools.JavaCompilerTool
import com.ahmadkharfan.androidstudiolite.build.engine.tools.KotlinCompileRequest
import com.ahmadkharfan.androidstudiolite.build.engine.tools.KotlinCompilerTool
import com.ahmadkharfan.androidstudiolite.build.engine.tools.SigningKey
import com.ahmadkharfan.androidstudiolite.build.engine.tools.Toolchain
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Verifies task ordering, Kotlin-skipping, incrementality and event reporting with fake tools. */
class BuildPipelineTest {

    private lateinit var project: File
    private lateinit var buildDir: File

    @Before fun setup() {
        project = Files.createTempDirectory("proj").toFile()
        buildDir = File(project, "build")
        File(project, "src/main/java/demo").mkdirs()
        File(project, "src/main/java/demo/Main.java").writeText("package demo; class Main {}")
        File(project, "src/main/res/values").mkdirs()
        File(project, "src/main/AndroidManifest.xml").writeText("<manifest/>")
    }

    private fun spec(withKotlin: Boolean): BuildSpec {
        val kotlinRoot = File(project, "src/main/kotlin").apply { if (withKotlin) { mkdirs(); File(this, "A.kt").writeText("class A") } }
        return BuildSpec(
            moduleName = "app",
            applicationId = "demo.app",
            minSdk = 24,
            targetSdk = 34,
            kotlinSourceRoots = if (withKotlin) listOf(kotlinRoot) else emptyList(),
            javaSourceRoots = listOf(File(project, "src/main/java")),
            resDirs = listOf(File(project, "src/main/res")),
            assetsDirs = emptyList(),
            manifest = File(project, "src/main/AndroidManifest.xml"),
            dependencyJars = emptyList(),
            dependencyResApks = emptyList(),
            toolchain = Toolchain(
                aapt2Binary = File(project, "aapt2"),
                androidJar = File(project, "android.jar").apply { writeText("jar") },
                kotlinCompilerClasspath = if (withKotlin) listOf(File(project, "kc.jar").apply { writeText("kc") }) else emptyList(),
            ),
            buildDir = buildDir,
            outputApk = File(buildDir, "out.apk"),
        )
    }

    @Test fun `task list skips kotlin when there are no kotlin sources`() {
        val pipeline = BuildPipeline(FakeAapt2(), FakeKotlin(), FakeJavac(), FakeDex(), FakeSigner())
        val javaOnly = pipeline.tasks(spec(withKotlin = false)).map { it.path }
        assertFalse(javaOnly.any { it.contains("Kotlin") })

        val withKotlin = pipeline.tasks(spec(withKotlin = true)).map { it.path }
        assertTrue(withKotlin.any { it.contains("Kotlin") })
    }

    @Test fun `full pipeline runs each task once and reports outcomes in order`() {
        val recorder = RecordingReporter()
        val pipeline = BuildPipeline(FakeAapt2(), FakeKotlin(), FakeJavac(), FakeDex(), FakeSigner())
        pipeline.run(spec(withKotlin = false), recorder, CancellationToken())

        assertEquals(
            listOf(
                ":app:compileDebugResources",
                ":app:linkDebugResources",
                ":app:compileDebugJava",
                ":app:dexDebug",
                ":app:packageDebug",
                ":app:signDebug",
            ),
            recorder.started,
        )
        assertTrue(recorder.finished.values.all { it == TaskOutcome.SUCCESS })
    }

    @Test fun `unchanged build is fully up-to-date on the second run`() {
        val pipeline = BuildPipeline(FakeAapt2(), FakeKotlin(), FakeJavac(), FakeDex(), FakeSigner())
        val s = spec(withKotlin = false)
        pipeline.run(s, BuildReporter.None, CancellationToken())

        val recorder = RecordingReporter()
        pipeline.run(s, recorder, CancellationToken())
        assertTrue("all tasks should be up-to-date", recorder.finished.values.all { it == TaskOutcome.UP_TO_DATE })
    }

    // ---- fakes: each produces the outputs its real counterpart would, so up-to-date checks work ----

    private class FakeAapt2 : Aapt2Tool {
        override fun compile(resDir: File, outputZip: File): Aapt2Result {
            outputZip.parentFile.mkdirs(); outputZip.writeText("compiled"); return Aapt2Result(0, "")
        }
        override fun link(request: Aapt2LinkRequest): Aapt2Result {
            request.outputApk.parentFile.mkdirs()
            // A real (minimal) zip, since the packager reads it with ZipFile.
            java.util.zip.ZipOutputStream(request.outputApk.outputStream()).use { zip ->
                zip.putNextEntry(java.util.zip.ZipEntry("AndroidManifest.xml")); zip.write("<manifest/>".toByteArray()); zip.closeEntry()
            }
            request.javaOutputDir.mkdirs(); File(request.javaOutputDir, "R.java").writeText("class R{}")
            return Aapt2Result(0, "")
        }
    }

    private class FakeKotlin : KotlinCompilerTool {
        override fun compile(request: KotlinCompileRequest): CompileResult {
            request.outputDir.mkdirs(); File(request.outputDir, "A.class").writeText("kt"); return CompileResult(true, emptyList(), "")
        }
    }

    private class FakeJavac : JavaCompilerTool {
        override fun compile(request: JavaCompileRequest): CompileResult {
            request.outputDir.mkdirs(); File(request.outputDir, "Main.class").writeText("j"); return CompileResult(true, emptyList(), "")
        }
    }

    private class FakeDex : DexTool {
        override fun dex(request: DexRequest) {
            if (request.output.name.endsWith(".zip")) { request.output.parentFile.mkdirs(); request.output.writeText("dexzip") }
            else { request.output.mkdirs(); File(request.output, "classes.dex").writeText("dex") }
        }
    }

    private class FakeSigner : ApkSignerTool {
        override fun sign(unsignedApk: File, signedApk: File, key: SigningKey, minSdkVersion: Int) {
            signedApk.parentFile.mkdirs(); signedApk.writeText("signed")
        }
    }

    private class RecordingReporter : BuildReporter {
        val started = mutableListOf<String>()
        val finished = LinkedHashMap<String, TaskOutcome>()
        override fun taskStarted(taskPath: String) { started += taskPath }
        override fun taskFinished(taskPath: String, outcome: TaskOutcome) { finished[taskPath] = outcome }
    }
}
