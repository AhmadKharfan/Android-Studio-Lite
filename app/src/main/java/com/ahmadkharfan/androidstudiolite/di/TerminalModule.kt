package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.core.linux.LinuxBootstrapInstaller
import com.ahmadkharfan.androidstudiolite.core.linux.ProotEnvironment
import com.ahmadkharfan.androidstudiolite.data.local.pty.PtyTerminalRepository
import com.ahmadkharfan.androidstudiolite.feature.terminal.TerminalSessionManager
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module
import java.io.File

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
