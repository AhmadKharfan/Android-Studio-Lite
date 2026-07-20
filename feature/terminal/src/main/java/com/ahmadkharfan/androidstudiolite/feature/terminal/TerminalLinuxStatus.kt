package com.ahmadkharfan.androidstudiolite.feature.terminal

import com.ahmadkharfan.androidstudiolite.core.linux.LinuxInstallState
import com.ahmadkharfan.androidstudiolite.core.linux.ProotEnvironment

fun LinuxInstallState.toLinuxStatus(proot: ProotEnvironment): LinuxStatus {
    val onDisk = proot.isInstalled()
    val installed = onDisk || this is LinuxInstallState.Installed
    return when (this) {
        LinuxInstallState.Unsupported -> LinuxStatus(supported = false, installed = installed)
        LinuxInstallState.NotInstalled -> LinuxStatus(installed = installed)
        is LinuxInstallState.Downloading -> LinuxStatus(
            installed = installed,
            isBusy = true,
            progressPercent = (progress * 100).toInt(),
            phase = "Downloading ${(progress * 100).toInt()}%",
        )
        LinuxInstallState.Extracting -> LinuxStatus(installed = installed, isBusy = true, phase = "Extracting…")
        LinuxInstallState.BootstrappingPackages -> LinuxStatus(
            installed = installed,
            isBusy = true,
            phase = "Installing git, python3, curl…",
        )
        LinuxInstallState.Installed -> LinuxStatus(installed = true)
        is LinuxInstallState.Failed -> LinuxStatus(
            installed = installed,
            error = message,
        )
    }
}
