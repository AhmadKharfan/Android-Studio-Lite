package com.ahmadkharfan.androidstudiolite.data.buildsystem.signing

import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreError
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreException
import com.ahmadkharfan.androidstudiolite.domain.signing.ReleaseKeystoreParams
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class KeystoreFilesTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun params(file: File) = ReleaseKeystoreParams(
        storeFile = file,
        storePassword = "storepass",
        keyAlias = "release",
        keyPassword = "keypass",
        validityYears = 25,
        commonName = "Ahmad Kharfan",
        organization = "ASL",
        country = "US",
    )

    @Test
    fun `create writes a loadable pkcs12 keystore with a valid self-signed cert`() {
        val file = File(tmp.root, "release.jks")
        val config = KeystoreFiles.create(params(file))
        assertTrue(file.isFile)
        assertEquals("release", config.keyAlias)

        val ks = KeyStore.getInstance(KeystoreFiles.KEYSTORE_TYPE)
        file.inputStream().use { ks.load(it, "storepass".toCharArray()) }
        assertTrue(ks.containsAlias("release"))
        assertNotNull(ks.getKey("release", "keypass".toCharArray()))

        val cert = ks.getCertificate("release") as X509Certificate

        cert.verify(cert.publicKey)
        assertTrue(cert.subjectX500Principal.name.contains("CN=Ahmad Kharfan"))

        assertTrue(cert.notAfter.after(cert.notBefore))
    }

    @Test
    fun `import round-trips a created keystore`() {
        val file = File(tmp.root, "release.jks")
        KeystoreFiles.create(params(file))
        val config = KeystoreFiles.import(file, "storepass", "release", "keypass")
        assertEquals("release", config.keyAlias)
        assertTrue(!config.isDebug)
    }

    @Test
    fun `import reports a wrong store password`() {
        val file = File(tmp.root, "release.jks")
        KeystoreFiles.create(params(file))
        try {
            KeystoreFiles.import(file, "nope-wrong", "release", "keypass")
            fail("expected KeystoreException")
        } catch (e: KeystoreException) {
            assertTrue(e.error is KeystoreError.WrongStorePassword)
        }
    }

    @Test
    fun `import reports a missing alias with the available list`() {
        val file = File(tmp.root, "release.jks")
        KeystoreFiles.create(params(file))
        try {
            KeystoreFiles.import(file, "storepass", "ghost", "keypass")
            fail("expected KeystoreException")
        } catch (e: KeystoreException) {
            val err = e.error
            assertTrue(err is KeystoreError.AliasNotFound)
            assertTrue((err as KeystoreError.AliasNotFound).availableAliases.contains("release"))
        }
    }

    @Test
    fun `import reports a missing file`() {
        try {
            KeystoreFiles.import(File(tmp.root, "absent.jks"), "x", "a", "b")
            fail("expected KeystoreException")
        } catch (e: KeystoreException) {
            assertTrue(e.error is KeystoreError.FileNotFound)
        }
    }

    @Test
    fun `create rejects a short password`() {
        try {
            KeystoreFiles.create(params(File(tmp.root, "x.jks")).copy(storePassword = "123"))
            fail("expected KeystoreException")
        } catch (e: KeystoreException) {
            assertTrue(e.error is KeystoreError.InvalidParams)
        }
    }

    @Test
    fun `create refuses to overwrite an existing keystore`() {
        val file = File(tmp.root, "existing.p12").apply { writeText("keep me") }
        try {
            KeystoreFiles.create(params(file))
            fail("expected KeystoreException")
        } catch (e: KeystoreException) {
            assertTrue(e.error is KeystoreError.InvalidParams)
            assertEquals("keep me", file.readText())
        }
    }

    @Test
    fun `create rejects an invalid country code`() {
        try {
            KeystoreFiles.create(params(File(tmp.root, "country.p12")).copy(country = "USA"))
            fail("expected KeystoreException")
        } catch (e: KeystoreException) {
            assertTrue(e.error is KeystoreError.InvalidParams)
        }
    }

    @Test
    fun `distinguished name is built from non-blank fields in order`() {
        val dn = params(File(tmp.root, "x.jks")).distinguishedName()
        assertEquals("CN=Ahmad Kharfan,O=ASL,C=US", dn)
    }
}
