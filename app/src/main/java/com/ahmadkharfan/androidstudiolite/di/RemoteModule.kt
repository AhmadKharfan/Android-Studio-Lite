package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.BuildConfig
import com.ahmadkharfan.androidstudiolite.data.remote.ArtifactDownloader
import com.ahmadkharfan.androidstudiolite.data.remote.ProjectPackager
import com.ahmadkharfan.androidstudiolite.data.remote.RemoteClient
import com.ahmadkharfan.androidstudiolite.data.remote.ServerSettingsRepository
import com.ahmadkharfan.androidstudiolite.data.remote.attestation.IntegrityTokenProvider
import com.ahmadkharfan.androidstudiolite.data.remote.attestation.NoopIntegrityTokenProvider
import com.ahmadkharfan.androidstudiolite.data.remote.attestation.PlayIntegrityTokenProvider
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val remoteModule = module {
    single { ServerSettingsRepository(androidContext()) }


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
}
