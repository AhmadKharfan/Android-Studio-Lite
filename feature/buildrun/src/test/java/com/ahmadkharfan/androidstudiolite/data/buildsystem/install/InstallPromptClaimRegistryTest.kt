package com.ahmadkharfan.androidstudiolite.data.buildsystem.install

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstallPromptClaimRegistryTest {
    @Test
    fun `a confirmation token can only be presented once until terminal cleanup`() {
        val token = "operation-1"

        InstallPromptClaimRegistry.clear(token)
        assertTrue(InstallPromptClaimRegistry.claim(token))
        assertFalse(InstallPromptClaimRegistry.claim(token))

        InstallPromptClaimRegistry.clear(token)
        assertTrue(InstallPromptClaimRegistry.claim(token))
        InstallPromptClaimRegistry.clear(token)
    }
}
