package com.ahmadkharfan.androidstudiolite.data.githubactions.build

import com.ahmadkharfan.androidstudiolite.domain.signing.ApkSigner
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreManager
import java.io.File

/**
 * Re-signs APKs and App Bundles built on GitHub with this device's keys: the debug key, or the release
 * keystore for release builds. Build machines never see a key, and every build installs over the
 * previous one instead of failing with a signature mismatch (each runner would otherwise sign with a
 * fresh key).
 */
class ApkSigning(
    private val signer: ApkSigner,
    private val keystores: KeystoreManager,
) {
    /** Signs [input] into [output] and returns the certificate's SHA-256. */
    internal suspend fun sign(input: File, output: File, release: Boolean): String =
        signer.sign(input, output, keystores.signingConfigFor(if (release) "release" else "debug"))

    /** Signs the App Bundle [input] into [output] and returns the certificate's SHA-256. */
    internal suspend fun signBundle(input: File, output: File, release: Boolean): String =
        signer.signBundle(input, output, keystores.signingConfigFor(if (release) "release" else "debug"))
}
