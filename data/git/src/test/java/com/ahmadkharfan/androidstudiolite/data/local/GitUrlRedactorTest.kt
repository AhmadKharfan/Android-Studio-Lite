package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.GitException
import org.eclipse.jgit.api.errors.TransportException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitUrlRedactorTest {
    @Test
    fun `strips user info while preserving remote components`() {
        assertEquals(
            "https://github.com:8443/owner/repo.git?x=1",
            GitUrlRedactor.stripUserInfo("https://user:secret@github.com:8443/owner/repo.git?x=1"),
        )
    }

    @Test
    fun `redacts embedded credentials from exception messages`() {
        val message = GitUrlRedactor.redact(
            "failed https://token@github.com/owner/private.git",
            "https://token@github.com/owner/private.git",
        )
        assertFalse(message.contains("token"))
        assertTrue(message.contains("https://github.com/owner/private.git"))
    }

    @Test
    fun `auth transport failures map to typed redacted errors`() {
        val url = "https://user:secret@example.com/repo.git"
        val mapped = JGitExceptionMapper.map(TransportException("Authentication failed for $url"), url)
        assertTrue(mapped is GitException.Auth)
        assertFalse(mapped.message.orEmpty().contains("secret"))
    }
}
