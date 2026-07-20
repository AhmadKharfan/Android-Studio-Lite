package com.ahmadkharfan.androidstudiolite.core.linux

import android.os.Build

object LinuxDistro {

    const val DISPLAY_NAME = "Alpine Linux"

    data class Rootfs(val url: String, val sha256: String)


    private const val BASE = "https://github.com/termux/proot-distro/releases/download/v4.30.1"

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

    fun forThisDevice(): Rootfs? {
        for (abi in Build.SUPPORTED_ABIS) byAbi[abi]?.let { return it }
        return null
    }

    fun isSupportedDevice(): Boolean = forThisDevice() != null
}
