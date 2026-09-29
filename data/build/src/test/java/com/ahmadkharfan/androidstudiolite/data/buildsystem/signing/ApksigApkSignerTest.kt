package com.ahmadkharfan.androidstudiolite.data.buildsystem.signing

import com.android.apksig.ApkVerifier
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreError
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreException
import com.ahmadkharfan.androidstudiolite.domain.signing.ReleaseKeystoreParams
import com.ahmadkharfan.androidstudiolite.domain.signing.SigningConfig
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ApksigApkSignerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val signer = ApksigApkSigner()

    private fun keystore(name: String, alias: String = "key0"): SigningConfig = KeystoreFiles.create(
        ReleaseKeystoreParams(
            storeFile = File(tmp.root, "$name.p12"),
            storePassword = "store-pass",
            keyAlias = alias,
            keyPassword = "store-pass",
            commonName = name,
        ),
    )

    /** A real, unsigned APK (compiled manifest only), built with aapt2 once and kept as a fixture. */
    private fun unsignedApk(): File = File(tmp.root, "app-debug.apk").apply {
        writeBytes(checkNotNull(ApksigApkSignerTest::class.java.getResourceAsStream("/signing/unsigned-fixture.apk")).use { it.readBytes() })
    }

    /** A zip without a manifest: apksig can't read its minimum SDK. */
    private fun manifestlessApk(): File = File(tmp.root, "bare.apk").apply {
        ZipOutputStream(outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("classes.dex"))
            zip.write("dex".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("res/values.xml"))
            zip.write("<resources/>".toByteArray())
            zip.closeEntry()
        }
    }

    private fun verify(apk: File) = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(24).setMaxCheckedPlatformVersion(34).build().verify()

    @Test
    fun `signed apk verifies and reports its certificate`() = runBlocking {
        val config = keystore("device-debug")
        val output = File(tmp.root, "out/signed.apk")

        val certSha = signer.sign(unsignedApk(), output, config)

        val result = verify(output)
        assertTrue(result.errors.toString(), result.isVerified)
        val cert = result.signerCertificates.single()
        val expected = MessageDigest.getInstance("SHA-256").digest(cert.encoded).joinToString("") { "%02x".format(it) }
        assertEquals(expected, certSha)
        assertFalse(File(tmp.root, "out/signed.apk.part").exists())
    }

    @Test
    fun `re-signing replaces the build machine's signature`() = runBlocking {
        val machine = File(tmp.root, "machine-signed.apk")
        signer.sign(unsignedApk(), machine, keystore("build-machine"))
        val device = keystore("device-debug", alias = "debug")

        val certSha = signer.sign(machine, File(tmp.root, "device-signed.apk"), device)

        val signers = verify(File(tmp.root, "device-signed.apk")).signerCertificates
        assertEquals(1, signers.size)
        val machineSha = signer.sign(unsignedApk(), File(tmp.root, "again.apk"), keystore("build-machine-2"))
        assertTrue(certSha != machineSha)
    }

    @Test
    fun `an apk whose manifest can't be read is still signed`() = runBlocking {
        val output = File(tmp.root, "bare-signed.apk")

        signer.sign(manifestlessApk(), output, keystore("device-debug"))

        val entries = java.util.zip.ZipFile(output).use { zip -> zip.entries().toList().map { it.name } }
        assertTrue(entries.toString(), entries.contains("META-INF/CERT.RSA"))
    }

    @Test
    fun `wrong key password is a keystore error`() = runBlocking {
        val config = keystore("device-debug").copy(keyPassword = "not-it")

        val error = runCatching { signer.sign(unsignedApk(), File(tmp.root, "x.apk"), config) }.exceptionOrNull()

        assertEquals(KeystoreError.WrongKeyPassword, (error as KeystoreException).error)
    }

    @Test
    fun `missing keystore is a keystore error`() = runBlocking {
        val config = keystore("device-debug").copy(storeFile = File(tmp.root, "gone.p12"))

        val error = runCatching { signer.sign(unsignedApk(), File(tmp.root, "x.apk"), config) }.exceptionOrNull()

        assertEquals(KeystoreError.FileNotFound, (error as KeystoreException).error)
    }
}
