package com.ahmadkharfan.androidstudiolite.data.build

import com.ahmadkharfan.androidstudiolite.build.engine.tools.Toolchain

/** Whether the on-device build toolchain is ready, and if so the resolved [Toolchain]. */
sealed interface ToolchainStatus {
    data class Ready(val toolchain: Toolchain) : ToolchainStatus

    /** Something required (android.jar, aapt2, kotlinc) is missing; [reason] is user-facing. */
    data class NotReady(val reason: String) : ToolchainStatus
}

/**
 * Resolves the play-flavor [Toolchain] from the installed on-device environment. Abstracted from
 * `Context` so the build system's mapping/sync logic stays unit-testable without an Android runtime;
 * the production implementation is [AndroidToolchainProvider].
 */
fun interface ToolchainProvider {
    /** @param compileSdk the platform API level whose `android.jar` the build compiles against. */
    fun toolchain(compileSdk: Int): ToolchainStatus
}
