package com.ahmadkharfan.androidstudiolite.di

import android.content.Context
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironment
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironmentPaths
import com.ahmadkharfan.androidstudiolite.data.local.ShellTerminalRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.TerminalRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/**
 * T7 terminal wiring. Kept as its own module (registered in `AslApplication`) rather than folded into
 * the central `dataModule`, so the real shell binding stays self-contained.
 */
val terminalModule: Module = module {
    single<TerminalRepository> {
        val context: Context = androidContext()
        ShellTerminalRepository(
            shellPathProvider = { resolveShellPath(context) },
            environmentProvider = { IdeEnvironment.environment(context) },
            defaultWorkingDirectory = { IdeEnvironmentPaths.home(context) },
        )
    }
}

/** Prefer the toolchain shell once installed; fall back to the system shell that always exists. */
private fun resolveShellPath(context: Context): String {
    val prefixShell = File(IdeEnvironmentPaths.prefix(context), "bin/sh")
    return if (prefixShell.exists()) prefixShell.absolutePath else "/system/bin/sh"
}
