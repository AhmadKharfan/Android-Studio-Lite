package com.ahmadkharfan.androidstudiolite.domain.signing

import java.io.File

/**
 * A resolved signing configuration the build backends use to sign an APK/AAB. Flavor-agnostic: both
 * the play in-process pipeline (apksig) and the full Gradle backend (a generated `signingConfigs`
 * block) consume the same fields.
 */
data class SigningConfig(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
    /** True for the auto-managed debug keystore; false for a user's release keystore. */
    val isDebug: Boolean,
)

/** User-supplied parameters for creating a brand-new release keystore. */
data class ReleaseKeystoreParams(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
    val validityYears: Int = 25,
    /** Certificate distinguished-name fields; blank ones are omitted. */
    val commonName: String = "",
    val organization: String = "",
    val organizationalUnit: String = "",
    val locality: String = "",
    val state: String = "",
    val country: String = "",
) {
    /** Builds an RFC-2253-ish DN string from the non-blank fields. */
    fun distinguishedName(): String = buildList {
        if (commonName.isNotBlank()) add("CN=${commonName.escapeDn()}")
        if (organizationalUnit.isNotBlank()) add("OU=${organizationalUnit.escapeDn()}")
        if (organization.isNotBlank()) add("O=${organization.escapeDn()}")
        if (locality.isNotBlank()) add("L=${locality.escapeDn()}")
        if (state.isNotBlank()) add("ST=${state.escapeDn()}")
        if (country.isNotBlank()) add("C=${country.escapeDn()}")
    }.joinToString(",").ifBlank { "CN=Unknown" }
}

private fun String.escapeDn(): String =
    replace("\\", "\\\\").replace(",", "\\,").trim()

/** Why a keystore create/import failed, mapped to user-facing UI copy. */
sealed interface KeystoreError {
    data class InvalidParams(val reason: String) : KeystoreError
    object FileNotFound : KeystoreError
    object WrongStorePassword : KeystoreError
    data class AliasNotFound(val availableAliases: List<String>) : KeystoreError
    object WrongKeyPassword : KeystoreError
    data class Io(val message: String) : KeystoreError
}

class KeystoreException(val error: KeystoreError) : Exception(error.toString())
