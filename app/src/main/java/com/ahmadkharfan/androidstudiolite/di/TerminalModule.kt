package com.ahmadkharfan.androidstudiolite.di

import android.content.Context
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironment
import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironmentPaths
import com.ahmadkharfan.androidstudiolite.data.local.pty.PtyTerminalRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.TerminalRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/**
 * Terminal wiring. Kept as its own module (registered in `AslApplication`) rather than folded into the
 * central `dataModule`, so the shell binding stays self-contained.
 *
 * T12 upgrades the binding from the line-oriented T7 shell to [PtyTerminalRepository], a real
 * pseudo-terminal so interactive/curses programs work. `TERM` is exported so those programs pick the
 * right capabilities; we exec a system/toolchain shell (always allowed to run), not an app-data binary.
 */
val terminalModule: Module = module {
    single<TerminalRepository> {
        val context: Context = androidContext()
        PtyTerminalRepository(
            shellCommandProvider = { listOf(resolveShellPath(context)) },
            environmentProvider = { IdeEnvironment.environment(context) + mapOf("TERM" to "xterm-256color") },
            defaultWorkingDirectory = { IdeEnvironmentPaths.home(context) },
        )
    }
}

/** Prefer the toolchain shell once installed; fall back to the system shell that always exists. */
private fun resolveShellPath(context: Context): String {
    val prefixShell = File(IdeEnvironmentPaths.prefix(context), "bin/sh")
    return if (prefixShell.exists()) prefixShell.absolutePath else "/system/bin/sh"
}
