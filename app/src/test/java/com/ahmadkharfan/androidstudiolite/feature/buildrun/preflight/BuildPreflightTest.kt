package com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BuildPreflightTest {

    private val plentyOfSpace = 50L * 1024 * 1024 * 1024

    @Test
    fun `agp 8 with jdk 11 warns about jdk 17`() {
        val warnings = CompatibilityChecker.check(ToolchainVersions(gradle = "8.2", agp = "8.1.0", jdkMajor = 11))
        assertTrue(warnings.any { it.severity == PreflightSeverity.WARNING && it.title.contains("JDK 17") })
    }

    @Test
    fun `agp 8 with jdk 17 and gradle 8 is clean`() {
        val warnings = CompatibilityChecker.check(ToolchainVersions(gradle = "8.2", agp = "8.1.0", jdkMajor = 17))
        assertTrue(warnings.none { it.severity == PreflightSeverity.WARNING })
    }

    @Test
    fun `agp 8 with gradle 7 warns`() {
        val warnings = CompatibilityChecker.check(ToolchainVersions(gradle = "7.6", agp = "8.0.0", jdkMajor = 17))
        assertTrue(warnings.any { it.title.contains("Gradle too old") })
    }

    @Test
    fun `missing agp is info not warning`() {
        val warnings = CompatibilityChecker.check(ToolchainVersions(gradle = "8.2", agp = null, jdkMajor = 17))
        assertEquals(PreflightSeverity.INFO, warnings.single().severity)
    }

    /**
     * The build runs on the server, so a nearly-full device is worth flagging but must never stop a
     * build that only needs room for a source zip.
     */
    @Test
    fun `an almost full device warns but never blocks`() {
        val result = BuildPreflight.run(ToolchainVersions(agp = "8.1", jdkMajor = 17, gradle = "8.2"), 10L * 1024 * 1024)
        assertFalse(result.hasBlocker)
        assertTrue(result.canProceed)
        assertEquals(PreflightSeverity.WARNING, result.warnings.single { it.title == "Low storage" }.severity)
    }

    @Test
    fun `storage too small for the source zip warns`() {
        val warning = StorageChecker.check(100L * 1024 * 1024)
        assertEquals(PreflightSeverity.WARNING, warning?.severity)
    }

    /** 1 GB was "low" when the toolchain and caches lived on-device; now it's plenty. */
    @Test
    fun `a gigabyte free is no longer worth warning about`() {
        assertNull(StorageChecker.check(1L * 1024 * 1024 * 1024))
    }

    @Test
    fun `ample storage produces no warning`() {
        assertNull(StorageChecker.check(plentyOfSpace))
    }

    @Test
    fun `version compare handles qualifiers and different lengths`() {
        assertTrue(compareVersions("8.0", "7.6.1") > 0)
        // A non-numeric qualifier segment ("rc") compares as 0, so "8.0-rc" is not treated as < "8.0".
        assertTrue(compareVersions("8.0-rc", "8.0") == 0)
        assertTrue(compareVersions("8.2.1", "8.2") > 0)
    }
}
