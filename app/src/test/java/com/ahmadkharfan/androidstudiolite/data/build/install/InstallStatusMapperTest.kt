package com.ahmadkharfan.androidstudiolite.data.build.install

import android.content.pm.PackageInstaller
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallStatusMapperTest {

    @Test
    fun `pending user action maps to needs-user-action`() {
        val outcome = InstallStatusMapper.map(
            PackageInstaller.STATUS_PENDING_USER_ACTION, null, null,
        )
        assertTrue(outcome is InstallStatusMapper.Outcome.NeedsUserAction)
    }

    @Test
    fun `success carries the package name`() {
        val outcome = InstallStatusMapper.map(
            PackageInstaller.STATUS_SUCCESS, null, "com.example.app",
        )
        assertEquals(
            "com.example.app",
            (outcome as InstallStatusMapper.Outcome.Success).packageName,
        )
    }

    @Test
    fun `storage failure produces a specific reason`() {
        val outcome = InstallStatusMapper.map(
            PackageInstaller.STATUS_FAILURE_STORAGE, "no space", null,
        )
        val msg = (outcome as InstallStatusMapper.Outcome.Failure).message
        assertTrue(msg.contains("storage", ignoreCase = true))
        assertTrue(msg.contains("no space"))
    }

    @Test
    fun `aborted maps to a cancelled reason`() {
        assertTrue(
            InstallStatusMapper.describe(PackageInstaller.STATUS_FAILURE_ABORTED, null)
                .contains("cancelled", ignoreCase = true),
        )
    }
}
