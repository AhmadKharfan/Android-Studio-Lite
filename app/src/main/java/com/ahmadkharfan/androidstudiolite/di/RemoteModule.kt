package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.data.remote.ArtifactDownloader
import com.ahmadkharfan.androidstudiolite.data.remote.ProjectPackager
import com.ahmadkharfan.androidstudiolite.data.remote.RemoteClient
import com.ahmadkharfan.androidstudiolite.data.remote.ServerSettingsRepository
import com.ahmadkharfan.androidstudiolite.feature.settings.server.ServerSettingsViewModel
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Wiring for the server-side build backend (A2): settings persistence, the OkHttp transport, the
 * project packager and artifact downloader, plus the build-server settings screen's ViewModel.
 * `RemoteBuildSystem` itself is bound in [buildRunModule] (it replaces the temporary FakeBuildSystem).
 * Registered from [com.ahmadkharfan.androidstudiolite.AslApplication].
 */
val remoteModule = module {
    single { ServerSettingsRepository(androidContext()) }
    single { RemoteClient(settings = get()) }
    single { ProjectPackager() }
    single { ArtifactDownloader(client = get(), downloadDir = File(androidContext().cacheDir, "build-artifacts")) }
    viewModel { ServerSettingsViewModel(settings = get(), client = get()) }
}
