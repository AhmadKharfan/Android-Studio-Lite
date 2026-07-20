package com.ahmadkharfan.androidstudiolite.domain.signing

import java.io.File

interface KeystoreManager {

    fun debugKeystoreFile(): File

    fun suggestedReleaseKeystoreFile(): File

    suspend fun debugSigningConfig(): SigningConfig

    suspend fun createReleaseKeystore(params: ReleaseKeystoreParams): SigningConfig

    suspend fun importReleaseKeystore(
        storeFile: File,
        storePassword: String,
        keyAlias: String,
        keyPassword: String,
    ): SigningConfig

    suspend fun releaseSigningConfig(): SigningConfig?

    suspend fun clearReleaseKeystore()

    suspend fun signingConfigFor(buildType: String): SigningConfig
}
