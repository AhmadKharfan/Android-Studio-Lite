package com.ahmadkharfan.androidstudiolite.build.engine.tools

import com.android.apksig.ApkSigner
import java.io.File

/** Signs an unsigned APK in place-ish (input → output) with the given key. */
fun interface ApkSignerTool {
    fun sign(unsignedApk: File, signedApk: File, key: SigningKey, minSdkVersion: Int)
}

/**
 * apksig-backed [ApkSignerTool] (AOSP, Apache-2.0). Applies v1 (JAR) + v2/v3 (APK Signature Scheme)
 * signatures in-process — no `apksigner` binary — which keeps the play flavor free of downloaded
 * executables. v1 is enabled so the APK installs on API < 24 too.
 */
class Apksigner : ApkSignerTool {
    override fun sign(unsignedApk: File, signedApk: File, key: SigningKey, minSdkVersion: Int) {
        val signerConfig = ApkSigner.SignerConfig.Builder(
            "CERT",
            key.privateKey,
            key.certificateChain,
        ).build()

        signedApk.parentFile?.mkdirs()
        ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(unsignedApk)
            .setOutputApk(signedApk)
            .setMinSdkVersion(minSdkVersion)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }
}
