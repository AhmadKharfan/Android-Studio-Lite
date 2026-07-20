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
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildRunCoordinator
import java.io.File
import kotlinx.coroutines.flow.first
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.activeBuildDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "active_build",
)

/**
 * Build / install / run wiring for the build UI (T11). Everything here targets only the [BuildSystem]
 * interface. The app builds server-side, so [BuildSystem] is bound to [RemoteBuildSystem], which
 * streams the control plane's [com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent]s
 * into the same console and downloads the resulting APK for the install/run flow. Its transport,
 * packager, downloader, and settings live in [remoteModule]; the installer, keystore manager,
 * notifier, and coordinator around it are backend-agnostic.
 */
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
    single {
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
}
