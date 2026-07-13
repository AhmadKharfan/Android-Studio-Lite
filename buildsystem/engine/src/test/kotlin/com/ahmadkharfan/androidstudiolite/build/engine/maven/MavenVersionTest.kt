package com.ahmadkharfan.androidstudiolite.build.engine.maven

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MavenVersionTest {

    @Test fun `numeric ordering`() {
        assertTrue(MavenVersion.isNewer("1.10.0", "1.9.0"))
        assertTrue(MavenVersion.isNewer("2.0.0", "1.99.99"))
        assertTrue(MavenVersion.isNewer("1.0.1", "1.0"))
    }

    @Test fun `release outranks pre-release`() {
        assertTrue(MavenVersion.isNewer("1.0.0", "1.0.0-alpha01"))
        assertTrue(MavenVersion.isNewer("1.0.0", "1.0.0-rc01"))
        assertTrue(MavenVersion.isNewer("1.0.0-beta", "1.0.0-alpha"))
        assertTrue(MavenVersion.isNewer("1.0.0-rc", "1.0.0-beta"))
    }

    @Test fun `equal versions compare equal`() {
        assertEquals(0, MavenVersion.compare("1.2.3", "1.2.3"))
    }

    @Test fun `androidx style versions`() {
        assertTrue(MavenVersion.isNewer("1.13.0", "1.12.0"))
        assertTrue(MavenVersion.isNewer("1.1.0-alpha06", "1.1.0-alpha05"))
    }
}
