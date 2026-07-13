package com.ahmadkharfan.androidstudiolite.build.engine.tools

import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.Date
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/** A private key plus its certificate chain, ready to hand to [ApkSignerTool]. */
data class SigningKey(
    val privateKey: PrivateKey,
    val certificateChain: List<X509Certificate>,
)

/**
 * Auto-generated debug keystore, mirroring AGP's conventions so a play-built debug APK is
 * indistinguishable from an Android-Studio one: PKCS12 keystore, alias `androiddebugkey`, password
 * `android`, a 2048-bit RSA key and a long-lived self-signed certificate. Generated once and reused;
 * the certificate is created with Bouncy Castle (no `keytool`/`sun.security` internals, so it runs on
 * ART as well as the desktop JVM).
 */
class DebugKeystore(private val keystoreFile: File) {

    fun load(): SigningKey {
        if (!keystoreFile.isFile) generate()
        val ks = KeyStore.getInstance("PKCS12")
        keystoreFile.inputStream().use { ks.load(it, STORE_PASSWORD) }
        val key = ks.getKey(ALIAS, KEY_PASSWORD) as PrivateKey
        val chain = ks.getCertificateChain(ALIAS).map { it as X509Certificate }
        return SigningKey(key, chain)
    }

    private fun generate() {
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048, SecureRandom()) }.generateKeyPair()

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 24L * 60 * 60 * 1000)               // yesterday, for clock skew
        val notAfter = Date(now + 30L * 365 * 24 * 60 * 60 * 1000)     // ~30 years
        val dn = X500Name("CN=Android Debug,O=Android,C=US")
        val serial = BigInteger.valueOf(now)

        val builder = JcaX509v3CertificateBuilder(dn, serial, notBefore, notAfter, dn, keyPair.public)
        val signer = JcaContentSignerBuilder("SHA256withRSA").build(keyPair.private)
        val cert = JcaX509CertificateConverter().getCertificate(builder.build(signer))

        val ks = KeyStore.getInstance("PKCS12")
        ks.load(null, STORE_PASSWORD)
        ks.setKeyEntry(ALIAS, keyPair.private, KEY_PASSWORD, arrayOf(cert))
        keystoreFile.parentFile?.mkdirs()
        keystoreFile.outputStream().use { ks.store(it, STORE_PASSWORD) }
    }

    companion object {
        const val ALIAS = "androiddebugkey"
        val STORE_PASSWORD = "android".toCharArray()
        val KEY_PASSWORD = "android".toCharArray()
    }
}
