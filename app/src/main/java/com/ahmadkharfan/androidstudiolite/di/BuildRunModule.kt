package com.ahmadkharfan.androidstudiolite.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.ApkInstaller
import com.ahmadkharfan.androidstudiolite.data.buildsystem.signing.AndroidKeystoreManager
import com.ahmadkharfan.androidstudiolite.data.remote.ActiveBuildStore
import com.ahmadkharfan.androidstudiolite.data.remote.ActiveBuildRepository
import com.ahmadkharfan.androidstudiolite.data.remote.RemoteBuildSystem
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.repository.GitRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreManager
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildNotifier
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildRunApi
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildRunCoordinator
import java.io.File
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.activeBuildDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "active_build",
)

val buildRunModule = module {
    single<ActiveBuildRepository> { ActiveBuildStore(androidContext().activeBuildDataStore) }
    single<BuildSystem> {
        val preferences = get<PreferencesRepository>()
        val gitRepository = get<GitRepository>()
        val keystoreManager = get<KeystoreManager>()
        RemoteBuildSystem(
            client = get(),
            packager = get(),
            artifactDownloader = get(),
            gradleReader = get(),
            sourceDir = File(androidContext().cacheDir, "build-sources"),
            preferGitSource = { preferences.observePreferences().first().preferGitSource },
            gitSourceResolver = { root -> gitRepository.remoteInfo(root) },
            releaseSigningResolver = { keystoreManager.releaseSigningConfig() },
        )
    }
    single<KeystoreManager> { AndroidKeystoreManager(androidContext()) }
    single { ApkInstaller(androidContext()) }
    single { BuildNotifier(androidContext()) }
    single<BuildRunCoordinator> {
        BuildRunCoordinator(
            context = androidContext(),
            buildSystem = get(),
            keystoreManager = get(),
            apkInstaller = get(),
            gradleReader = get(),
            notifier = get(),
            activeBuildStore = get(),
        )
    }
    single<BuildRunApi> { get<BuildRunCoordinator>() }
}
