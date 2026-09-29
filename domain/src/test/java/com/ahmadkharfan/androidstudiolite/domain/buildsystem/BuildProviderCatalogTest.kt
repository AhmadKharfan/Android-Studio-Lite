package com.ahmadkharfan.androidstudiolite.domain.buildsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class BuildProviderCatalogTest {

    private val catalog = BuildProviderCatalog(
        available = listOf(BuildProviderIds.REMOTE, BuildProviderIds.GITHUB_ACTIONS),
        defaultProviderId = BuildProviderIds.REMOTE,
    )

    @Test
    fun `no choice means the default`() {
        assertEquals(BuildProviderIds.REMOTE, catalog.effective(null))
    }

    @Test
    fun `an available choice is honoured`() {
        assertEquals(BuildProviderIds.GITHUB_ACTIONS, catalog.effective(BuildProviderIds.GITHUB_ACTIONS))
    }

    @Test
    fun `a choice this build doesn't offer falls back to the default`() {
        assertEquals(BuildProviderIds.REMOTE, catalog.effective("retired"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `the default must be available`() {
        BuildProviderCatalog(listOf(BuildProviderIds.REMOTE), defaultProviderId = BuildProviderIds.GITHUB_ACTIONS)
    }
}
