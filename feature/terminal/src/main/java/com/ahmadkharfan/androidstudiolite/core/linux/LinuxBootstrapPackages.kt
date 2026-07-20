package com.ahmadkharfan.androidstudiolite.core.linux

object LinuxBootstrapPackages {
    val packages: List<String> = listOf(
        "git",
        "python3",
        "curl",
        "wget",
        "openssh-client",
        "build-base",
    )

    fun apkInstallScript(): String =
        "apk update && apk add --no-cache ${packages.joinToString(" ")}"
}
