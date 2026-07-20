package com.ahmadkharfan.androidstudiolite.core.linux

/** Popular CLI tools installed automatically after the Alpine rootfs (via `apk`, not bundled in the APK). */
object LinuxBootstrapPackages {
    val packages: List<String> = listOf(
        "git",
        "python3",
        "curl",
        "wget",
        "openssh-client",
        "build-base",
    )

    fun apkInstallScript(): String = "apk add --no-cache ${packages.joinToString(" ")}"
}
