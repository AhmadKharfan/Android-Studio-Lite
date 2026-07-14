package com.ahmadkharfan.androidstudiolite.data.buildsystem.signing

import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreError
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreException
import com.ahmadkharfan.androidstudiolite.domain.signing.ReleaseKeystoreParams
import com.ahmadkharfan.androidstudiolite.domain.signing.SigningConfig
import java.io.File
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.UnrecoverableKeyException
import java.security.cert.Certificate

/**
 * Context-free keystore file operations: generate a PKCS12 keystore holding a self-signed RSA key,
 * and validate/import an existing one. PKCS12 is used (not JKS) because it is the one file keystore
 * format available on both Android and the desktop JVM, and apksigner reads it directly — which also
 * means this whole path is unit-testable off-device.
 */
internal object KeystoreFiles {

    const val KEYSTORE_TYPE = "PKCS12"
    private const val KEY_SIZE = 2048
    private const val MIN_PASSWORD_LENGTH = 6

    /** Generates a new keystore file per [params] and returns the resulting [SigningConfig]. */
    fun create(params: ReleaseKeystoreParams, isDebug: Boolean = false): SigningConfig {
        validate(params)
        val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(KEY_SIZE) }.generateKeyPair()
        val cert = SelfSignedCertGenerator.generate(
            keyPair = keyPair,
            dname = params.distinguishedName(),
            validityDays = params.validityYears.coerceAtLeast(1) * 365,
        )
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE).apply { load(null, null) }
        keyStore.setKeyEntry(
            params.keyAlias,
            keyPair.private,
            params.keyPassword.toCharArray(),
            arrayOf<Certificate>(cert),
        )
        params.storeFile.parentFile?.mkdirs()
        try {
            params.storeFile.outputStream().use { keyStore.store(it, params.storePassword.toCharArray()) }
        } catch (e: java.io.IOException) {
            throw KeystoreException(KeystoreError.Io(e.message ?: "Could not write keystore"))
        }
        return SigningConfig(
            storeFile = params.storeFile,
            storePassword = params.storePassword,
            keyAlias = params.keyAlias,
            keyPassword = params.keyPassword,
            isDebug = isDebug,
        )
    }

    /**
     * Loads [storeFile], verifies [storePassword], that [keyAlias] exists, and that its key is
     * recoverable with [keyPassword]; throws [KeystoreException] with a specific [KeystoreError]
     * otherwise. Returns the validated [SigningConfig] on success.
     */
    fun import(storeFile: File, storePassword: String, keyAlias: String, keyPassword: String): SigningConfig {
        if (!storeFile.isFile) throw KeystoreException(KeystoreError.FileNotFound)
        val keyStore = KeyStore.getInstance(KEYSTORE_TYPE)
        try {
            storeFile.inputStream().use { keyStore.load(it, storePassword.toCharArray()) }
        } catch (e: java.io.IOException) {
            // PKCS12 surfaces a bad store password as an IOException with a wrapped cause.
            throw KeystoreException(KeystoreError.WrongStorePassword)
        }
        if (!keyStore.containsAlias(keyAlias)) {
            throw KeystoreException(KeystoreError.AliasNotFound(keyStore.aliases().toList()))
        }
        try {
            keyStore.getKey(keyAlias, keyPassword.toCharArray())
        } catch (e: UnrecoverableKeyException) {
            throw KeystoreException(KeystoreError.WrongKeyPassword)
        }
        return SigningConfig(storeFile, storePassword, keyAlias, keyPassword, isDebug = false)
    }

    private fun validate(params: ReleaseKeystoreParams) {
        val problems = buildList {
            if (params.keyAlias.isBlank()) add("Key alias is required")
            if (params.storePassword.length < MIN_PASSWORD_LENGTH) add("Store password must be at least $MIN_PASSWORD_LENGTH characters")
            if (params.keyPassword.length < MIN_PASSWORD_LENGTH) add("Key password must be at least $MIN_PASSWORD_LENGTH characters")
            if (params.validityYears < 1) add("Validity must be at least 1 year")
            if (params.distinguishedName().isBlank()) add("At least one certificate field is required")
        }
        if (problems.isNotEmpty()) throw KeystoreException(KeystoreError.InvalidParams(problems.joinToString("; ")))
    }
}
