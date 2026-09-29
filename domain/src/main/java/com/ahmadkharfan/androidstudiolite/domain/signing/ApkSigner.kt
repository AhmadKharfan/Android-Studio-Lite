package com.ahmadkharfan.androidstudiolite.domain.signing

import java.io.File

/**
 * Signs APKs and App Bundles on the device, so signing keys never leave it and every build of a project
 * installs over the previous one, whichever machine compiled it.
 */
interface ApkSigner {

    /**
     * Writes [input], re-signed with [config], to [output] (any existing signatures are replaced).
     *
     * @return the SHA-256 of the signing certificate, lower-case hex.
     * @throws KeystoreException when the keystore can't be read.
     */
    suspend fun sign(input: File, output: File, config: SigningConfig): String

    /**
     * Writes the App Bundle [input], signed with [config] the way `jarsigner` would, to [output].
     *
     * @return the SHA-256 of the signing certificate, lower-case hex.
     * @throws KeystoreException when the keystore can't be read.
     */
    suspend fun signBundle(input: File, output: File, config: SigningConfig): String
}
