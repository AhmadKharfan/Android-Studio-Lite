package com.ahmadkharfan.androidstudiolite.domain.signing

import java.io.File

data class SigningConfig(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
    val isDebug: Boolean,
)

data class ReleaseKeystoreParams(
    val storeFile: File,
    val storePassword: String,
    val keyAlias: String,
    val keyPassword: String,
    val validityYears: Int = 25,
    val commonName: String = "",
    val organization: String = "",
    val organizationalUnit: String = "",
    val locality: String = "",
    val state: String = "",
    val country: String = "",
) {
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

sealed interface KeystoreError {
    data class InvalidParams(val reason: String) : KeystoreError
    object FileNotFound : KeystoreError
    object WrongStorePassword : KeystoreError
    data class AliasNotFound(val availableAliases: List<String>) : KeystoreError
    object WrongKeyPassword : KeystoreError
    data class Io(val message: String) : KeystoreError
}

class KeystoreException(val error: KeystoreError) : Exception(error.toString())
