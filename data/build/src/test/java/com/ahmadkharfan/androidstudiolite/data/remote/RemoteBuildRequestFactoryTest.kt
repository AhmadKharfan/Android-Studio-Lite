package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildKind
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.domain.model.GitRemoteInfo
import com.ahmadkharfan.androidstudiolite.domain.signing.SigningConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RemoteBuildRequestFactoryTest {

    private val root = File("/tmp/project")

    private fun request(variant: String, kind: BuildKind = BuildKind.ASSEMBLE) =
        BuildRequest(projectRoot = root, modulePath = ":app", variantName = variant, kind = kind)


    @Test
    fun `null git source produces a zip request with no git fields`() {
        val req = RemoteBuildRequestFactory.create(request("debug"), gitSource = null, signing = null)

        assertEquals("zip", req.sourceType)
        assertNull(req.gitUrl)
        assertNull(req.ref)
        assertEquals(":app", req.modulePath)
        assertEquals("debug", req.variant)
        assertEquals(listOf(":app:assembleDebug"), req.tasks)
    }

    @Test
    fun `git source produces a git request carrying url and ref, no upload needed`() {
        val git = GitRemoteInfo(url = "https://example.com/app.git", ref = "main")

        val req = RemoteBuildRequestFactory.create(request("debug"), gitSource = git, signing = null)

        assertEquals("git", req.sourceType)
        assertEquals("https://example.com/app.git", req.gitUrl)
        assertEquals("main", req.ref)
    }

    @Test
    fun `shouldUseGit follows the preference but never for clean`() {
        assertTrue(RemoteBuildRequestFactory.shouldUseGit(preferGit = true, request("debug")))
        assertEquals(false, RemoteBuildRequestFactory.shouldUseGit(preferGit = false, request("debug")))
        assertEquals(
            false,
            RemoteBuildRequestFactory.shouldUseGit(preferGit = true, request("debug", BuildKind.CLEAN)),
        )
    }

    @Test
    fun `private git source falls back to zip selection`() {
        val private = GitRemoteInfo("https://example.com/private.git", "main", requiresAuth = true)

        assertNull(RemoteBuildRequestFactory.eligibleGitSource(private))
        val request = RemoteBuildRequestFactory.create(request("debug"), private, null)
        assertEquals("zip", request.sourceType)
        assertNull(request.gitUrl)
    }

    @Test
    fun `tasks map bundle and clean kinds`() {
        assertEquals(listOf(":app:bundleRelease"), RemoteBuildRequestFactory.defaultTasks(request("release", BuildKind.BUNDLE)))
        assertEquals(listOf("clean"), RemoteBuildRequestFactory.defaultTasks(request("debug", BuildKind.CLEAN)))
        assertEquals(
            listOf(":composeApp:assembleDevelopmentDebug"),
            RemoteBuildRequestFactory.defaultTasks(
                BuildRequest(root, ":composeApp", "developmentDebug", BuildKind.ASSEMBLE),
            ),
        )
    }


    @Test
    fun `flavored release names are detected as release variants`() {
        assertTrue(RemoteBuildRequestFactory.isReleaseVariant("release"))
        assertTrue(RemoteBuildRequestFactory.isReleaseVariant("developmentRelease"))
        assertEquals(false, RemoteBuildRequestFactory.isReleaseVariant("developmentDebug"))
    }

    @Test
    fun `release variant with a user keystore yields a signing payload over the wire`() {
        val config = SigningConfig(
            storeFile = File("/keys/release.jks"),
            storePassword = "storePass",
            keyAlias = "upload",
            keyPassword = "keyPass",
            isDebug = false,
        )
        val signing = RemoteBuildRequestFactory.releaseSigningMaterial(
            config = config,
            readBytes = { byteArrayOf(1, 2, 3) },
            encodeBase64 = { "b64:${it.size}" },
        )

        assertEquals("b64:3", signing!!.keystoreBase64)
        assertEquals("storePass", signing.storePassword)
        assertEquals("upload", signing.keyAlias)
        assertEquals("keyPass", signing.keyPassword)
        assertEquals("release.jks", signing.keystoreName)


        val req = RemoteBuildRequestFactory.create(request("release"), gitSource = null, signing = signing)
        assertEquals("release", req.variant)
        assertEquals(listOf(":app:assembleRelease"), req.tasks)
        assertEquals(signing, req.signing)
    }

    @Test
    fun `debug keystore is never uploaded`() {
        val debug = SigningConfig(File("/keys/debug.keystore"), "android", "androiddebugkey", "android", isDebug = true)

        val signing = RemoteBuildRequestFactory.releaseSigningMaterial(
            config = debug,
            readBytes = { byteArrayOf(9) },
            encodeBase64 = { "x" },
        )

        assertNull(signing)
    }

    @Test
    fun `no keystore or missing file yields no signing payload`() {
        assertNull(RemoteBuildRequestFactory.releaseSigningMaterial(config = null, encodeBase64 = { "x" }))

        val config = SigningConfig(File("/keys/release.jks"), "p", "a", "k", isDebug = false)
        assertNull(
            RemoteBuildRequestFactory.releaseSigningMaterial(
                config = config,
                readBytes = { null },
                encodeBase64 = { "x" },
            ),
        )
    }
}
