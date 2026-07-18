package com.ahmadkharfan.androidstudiolite.core.linux

import android.os.Build

/**
 * The downloadable Linux userland that the terminal runs under proot. We use Alpine Linux built by
 * the proot-distro project: it is tiny (~3.4 MB compressed), ships the `apk` package manager, and its
 * binaries are 64 KB-page aligned so they load on 4 KB / 16 KB / 64 KB-page Android kernels (i.e. it
 * works on Android 15's 16 KB devices). Nothing is bundled in the APK — the tarball is downloaded on
 * first use and extracted into app-private storage, so the APK size is unaffected.
 */
object LinuxDistro {

    const val DISPLAY_NAME = "Alpine Linux"

    /** A per-ABI root filesystem tarball (xz-compressed tar) plus its expected SHA-256. */
    data class Rootfs(val url: String, val sha256: String)

    // proot-distro release v4.30.1 rootfs artifacts (Alpine 3.22). Hosted on GitHub releases, which
    // are immutable, so the pinned URL + checksum stay valid.
    private const val BASE = "https://github.com/termux/proot-distro/releases/download/v4.30.1"

    /** Android ABI -> rootfs. Ordered lookup follows [Build.SUPPORTED_ABIS] (best ABI first). */
    private val byAbi: Map<String, Rootfs> = mapOf(
        "arm64-v8a" to Rootfs(
            "$BASE/alpine-aarch64-pd-v4.30.1.tar.xz",
            "bb23e51cd5b5ae56bf946a34992876902de1bb2ecc0f639d59c702c6371adc62",
        ),
        "armeabi-v7a" to Rootfs(
            "$BASE/alpine-arm-pd-v4.30.1.tar.xz",
            "ca1039d26481b63a412cd39d699c7f559c40ed5c532573c720e00218b5af0fd4",
        ),
        "x86_64" to Rootfs(
            "$BASE/alpine-x86_64-pd-v4.30.1.tar.xz",
            "0890920f83becc1c3529ca53fc71d7516a01d3de4139fbe936c8c60c6c32f8d1",
        ),
        "x86" to Rootfs(
            "$BASE/alpine-i686-pd-v4.30.1.tar.xz",
            "11ae8bbd14f789b260eb5b0c89d267e427d57e9adb1e1f71458a911c4891ecae",
        ),
    )

    /** The rootfs for this device's preferred supported ABI, or null on an unsupported architecture. */
    fun forThisDevice(): Rootfs? {
        for (abi in Build.SUPPORTED_ABIS) byAbi[abi]?.let { return it }
        return null
    }

    fun isSupportedDevice(): Boolean = forThisDevice() != null
}
