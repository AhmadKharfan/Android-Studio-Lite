package com.ahmadkharfan.androidstudiolite.domain.signing

import java.io.File

/**
 * Manages the keystores used to sign builds. The debug keystore is auto-created and reused; a release
 * keystore is created or imported by the user. Flavor-agnostic — both build backends read the
 * [SigningConfig] this returns.
 *
 * All failing operations throw [KeystoreException] carrying a specific [KeystoreError].
 */
interface KeystoreManager {

    /** The on-device path of the auto-managed debug keystore (`$HOME/.android/debug.keystore`). */
    fun debugKeystoreFile(): File

    /** A sensible default path to offer when creating a new release keystore. */
    fun suggestedReleaseKeystoreFile(): File

    /** Ensures the debug keystore exists (generating it on first use) and returns its config. */
    suspend fun debugSigningConfig(): SigningConfig

    /** Creates a new release keystore file and remembers it as the release signing config. */
    suspend fun createReleaseKeystore(params: ReleaseKeystoreParams): SigningConfig

    /** Validates an existing keystore and remembers it as the release signing config. */
    suspend fun importReleaseKeystore(
        storeFile: File,
        storePassword: String,
        keyAlias: String,
        keyPassword: String,
    ): SigningConfig

    /** The remembered release signing config, or null if the user hasn't set one up. */
    suspend fun releaseSigningConfig(): SigningConfig?

    /** Forgets the remembered release keystore (does not delete the file). */
    suspend fun clearReleaseKeystore()

    /**
     * Resolves the signing config for a build type: the release keystore for "release" (when set,
     * else falls back to debug), and the debug keystore for everything else.
     */
    suspend fun signingConfigFor(buildType: String): SigningConfig
}
