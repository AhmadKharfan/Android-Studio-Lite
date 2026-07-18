package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.core.linux.LinuxBootstrapInstaller
import com.ahmadkharfan.androidstudiolite.core.linux.ProotEnvironment
import com.ahmadkharfan.androidstudiolite.data.local.pty.PtyTerminalRepository
import com.ahmadkharfan.androidstudiolite.feature.terminal.TerminalSessionManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

/**
 * Terminal wiring. Kept as its own module (registered in `AslApplication`) so the shell binding stays
 * self-contained.
 *
 * Exposes a single [TerminalSessionManager] that mints a fresh [PtyTerminalRepository] per tab, so
 * multiple independent sessions can run at once. Each session forks a real pseudo-terminal. The
 * command + environment come from [ProotEnvironment]: once the user installs the Linux userland it
 * launches an Alpine shell under proot (so any tool can run); until then it falls back to the system
 * shell so the terminal always works. [LinuxBootstrapInstaller] downloads that userland on demand.
 */
val terminalModule: Module = module {
    single { ProotEnvironment(androidContext()) }
    single { LinuxBootstrapInstaller(androidContext(), get()) }
    single {
        val proot: ProotEnvironment = get()
        TerminalSessionManager(
            repositoryFactory = { hostWorkingDir ->
                val hostDir = hostWorkingDir?.let(::File)
                PtyTerminalRepository(
                    shellCommandProvider = { proot.shellCommand(hostDir) },
                    environmentProvider = { proot.environment() },
                    defaultWorkingDirectory = { proot.workingDirectory(hostWorkingDir) },
                )
            },
        )
    }
}
