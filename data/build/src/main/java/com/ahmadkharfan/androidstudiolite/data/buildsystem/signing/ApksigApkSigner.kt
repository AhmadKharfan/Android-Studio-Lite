package com.ahmadkharfan.androidstudiolite.data.buildsystem.signing

import com.android.apksig.ApkSigner as Apksig
import com.android.apksig.apk.MinSdkVersionException
import com.ahmadkharfan.androidstudiolite.domain.signing.ApkSigner
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreError
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreException
import com.ahmadkharfan.androidstudiolite.domain.signing.SigningConfig
import java.io.File
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.UnrecoverableKeyException
import java.security.cert.X509Certificate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [ApkSigner] backed by `apksig`, the library Android's own `apksigner` uses. Signs with the v1, v2 and
 * v3 schemes, replacing whatever signature the APK was built with.
 */
class ApksigApkSigner : ApkSigner {

    override suspend fun sign(input: File, output: File, config: SigningConfig): String = withContext(Dispatchers.IO) {
        val keyStore = KeystoreFiles.load(config.storeFile, config.storePassword)
        val key = try {
            keyStore.getKey(config.keyAlias, config.keyPassword.toCharArray()) as? PrivateKey
        } catch (e: UnrecoverableKeyException) {
            throw KeystoreException(KeystoreError.WrongKeyPassword, e)
        } ?: throw KeystoreException(KeystoreError.AliasNotFound(keyStore.aliases().toList()))
        val chain = keyStore.getCertificateChain(config.keyAlias).orEmpty().filterIsInstance<X509Certificate>()
        val signer = Apksig.SignerConfig.Builder(SIGNER_NAME, key, chain).build()
        output.parentFile?.mkdirs()
        val partial = File(output.path + ".part")
        try {
            signWith(signer, input, partial)
            if (output.exists()) output.delete()
            check(partial.renameTo(output)) { "Couldn't write the signed APK to $output" }
        } finally {
            partial.delete()
        }
        MessageDigest.getInstance("SHA-256").digest(chain.first().encoded).joinToString("") { "%02x".format(it) }
    }

    private fun signWith(signer: Apksig.SignerConfig, input: File, output: File) {
        fun builder() = Apksig.Builder(listOf(signer))
            .setInputApk(input)
            .setOutputApk(output)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
        val failure = runCatching { builder().build().sign() }.exceptionOrNull() ?: return
        // The minimum SDK is normally read from the manifest; if it can't be, assume the oldest version
        // this app targets rather than refusing to sign.
        if (failure !is MinSdkVersionException) throw failure
        builder().setMinSdkVersion(FALLBACK_MIN_SDK).build().sign()
    }

    private companion object {
        const val SIGNER_NAME = "CERT"
        const val FALLBACK_MIN_SDK = 21
    }
}
