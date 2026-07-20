package com.ahmadkharfan.androidstudiolite.data.buildsystem.signing

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironmentPaths
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreManager
import com.ahmadkharfan.androidstudiolite.domain.signing.ReleaseKeystoreParams
import com.ahmadkharfan.androidstudiolite.domain.signing.SigningConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidKeystoreManager(private val context: Context) : KeystoreManager {

    private val androidDir: File
        get() = File(IdeEnvironmentPaths.home(context), ".android").apply { if (!exists()) mkdirs() }

    private val securePrefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "asl_signing",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    override fun debugKeystoreFile(): File = File(androidDir, "debug.keystore")

    override fun suggestedReleaseKeystoreFile(): File = File(androidDir, "release.keystore")

    override suspend fun debugSigningConfig(): SigningConfig = withContext(Dispatchers.IO) {
        val file = debugKeystoreFile()
        if (!file.isFile) {
            KeystoreFiles.create(
                ReleaseKeystoreParams(
                    storeFile = file,
                    storePassword = DEBUG_PASSWORD,
                    keyAlias = DEBUG_ALIAS,
                    keyPassword = DEBUG_PASSWORD,
                    validityYears = DEBUG_VALIDITY_YEARS,
                    commonName = "Android Debug",
                    organization = "Android",
                    country = "US",
                ),
                isDebug = true,
            )
        } else {
            SigningConfig(file, DEBUG_PASSWORD, DEBUG_ALIAS, DEBUG_PASSWORD, isDebug = true)
        }
    }

    override suspend fun createReleaseKeystore(params: ReleaseKeystoreParams): SigningConfig =
        withContext(Dispatchers.IO) {
            KeystoreFiles.create(params).also { persistRelease(it) }
        }

    override suspend fun importReleaseKeystore(
        storeFile: File,
        storePassword: String,
        keyAlias: String,
        keyPassword: String,
    ): SigningConfig = withContext(Dispatchers.IO) {
        KeystoreFiles.import(storeFile, storePassword, keyAlias, keyPassword).also { persistRelease(it) }
    }

    override suspend fun releaseSigningConfig(): SigningConfig? = withContext(Dispatchers.IO) {
        val path = securePrefs.getString(KEY_STORE_FILE, null) ?: return@withContext null
        val file = File(path)
        if (!file.isFile) return@withContext null
        SigningConfig(
            storeFile = file,
            storePassword = securePrefs.getString(KEY_STORE_PASSWORD, "").orEmpty(),
            keyAlias = securePrefs.getString(KEY_ALIAS, "").orEmpty(),
            keyPassword = securePrefs.getString(KEY_KEY_PASSWORD, "").orEmpty(),
            isDebug = false,
        )
    }

    override suspend fun clearReleaseKeystore() = withContext(Dispatchers.IO) {
        securePrefs.edit()
            .remove(KEY_STORE_FILE).remove(KEY_STORE_PASSWORD).remove(KEY_ALIAS).remove(KEY_KEY_PASSWORD)
            .apply()
    }

    override suspend fun signingConfigFor(buildType: String): SigningConfig =
        if (buildType.equals("release", ignoreCase = true)) {
            releaseSigningConfig() ?: debugSigningConfig()
        } else {
            debugSigningConfig()
        }

    private fun persistRelease(config: SigningConfig) {
        securePrefs.edit()
            .putString(KEY_STORE_FILE, config.storeFile.absolutePath)
            .putString(KEY_STORE_PASSWORD, config.storePassword)
            .putString(KEY_ALIAS, config.keyAlias)
            .putString(KEY_KEY_PASSWORD, config.keyPassword)
            .apply()
    }

    companion object {

        const val DEBUG_ALIAS = "androiddebugkey"
        const val DEBUG_PASSWORD = "android"
        private const val DEBUG_VALIDITY_YEARS = 30

        private const val KEY_STORE_FILE = "release_store_file"
        private const val KEY_STORE_PASSWORD = "release_store_password"
        private const val KEY_ALIAS = "release_key_alias"
        private const val KEY_KEY_PASSWORD = "release_key_password"
    }
}
