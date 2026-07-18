package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.BuildConfig
import com.ahmadkharfan.androidstudiolite.data.remote.ArtifactDownloader
import com.ahmadkharfan.androidstudiolite.data.remote.ProjectPackager
import com.ahmadkharfan.androidstudiolite.data.remote.RemoteClient
import com.ahmadkharfan.androidstudiolite.data.remote.ServerSettingsRepository
import com.ahmadkharfan.androidstudiolite.data.remote.attestation.IntegrityTokenProvider
import com.ahmadkharfan.androidstudiolite.data.remote.attestation.NoopIntegrityTokenProvider
import com.ahmadkharfan.androidstudiolite.data.remote.attestation.PlayIntegrityTokenProvider
import com.ahmadkharfan.androidstudiolite.feature.settings.server.ServerSettingsViewModel
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * Wiring for the server-side build backend (A2): settings persistence, the OkHttp transport, the
 * project packager and artifact downloader, plus the build-server settings screen's ViewModel.
 * `RemoteBuildSystem` itself is bound in [buildRunModule].
 * Registered from [com.ahmadkharfan.androidstudiolite.AslApplication].
 */
val remoteModule = module {
    single { ServerSettingsRepository(androidContext()) }
    // Play Integrity attestation (A3). Gated by BuildConfig so dev builds (no Play Services, unlinked
    // cloud project) register without it; the real provider fails soft to null anyway.
    single<IntegrityTokenProvider> {
        val projectNumber = BuildConfig.PLAY_INTEGRITY_CLOUD_PROJECT_NUMBER
        if (BuildConfig.PLAY_INTEGRITY_ENABLED && projectNumber > 0L) {
            PlayIntegrityTokenProvider(androidContext(), projectNumber)
        } else {
            NoopIntegrityTokenProvider
        }
    }
    single { RemoteClient(settings = get(), integrityProvider = get()) }
    single { ProjectPackager() }
    single { ArtifactDownloader(client = get(), downloadDir = File(androidContext().cacheDir, "build-artifacts")) }
    viewModel { ServerSettingsViewModel(settings = get(), client = get()) }
}
