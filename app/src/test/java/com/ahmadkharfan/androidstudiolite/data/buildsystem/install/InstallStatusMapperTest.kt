package com.ahmadkharfan.androidstudiolite.data.buildsystem.install

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
    fun `conflict maps to its own outcome, not a generic failure`() {
        val outcome = InstallStatusMapper.map(
            PackageInstaller.STATUS_FAILURE_CONFLICT, "signatures do not match", "com.example.app",
        )


        val conflict = outcome as InstallStatusMapper.Outcome.Conflict
        assertEquals("com.example.app", conflict.packageName)
        assertTrue(conflict.message.contains("Conflicts with an existing package"))
        assertTrue(conflict.message.contains("signatures do not match"))
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
