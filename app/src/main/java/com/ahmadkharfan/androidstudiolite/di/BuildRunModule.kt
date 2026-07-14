package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.ApkInstaller
import com.ahmadkharfan.androidstudiolite.data.buildsystem.signing.AndroidKeystoreManager
import com.ahmadkharfan.androidstudiolite.data.remote.RemoteBuildSystem
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreManager
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildNotifier
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildRunCoordinator
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Build / install / run wiring for the build UI (T11). Everything here targets only the [BuildSystem]
 * interface. The app builds server-side, so [BuildSystem] is bound to [RemoteBuildSystem], which
 * streams the control plane's [com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent]s
 * into the same console and downloads the resulting APK for the install/run flow. Its transport,
 * packager, downloader, and settings live in [remoteModule]; the installer, keystore manager,
 * notifier, and coordinator around it are backend-agnostic.
 */
val buildRunModule = module {
    single<BuildSystem> {
        RemoteBuildSystem(
            client = get(),
            packager = get(),
            artifactDownloader = get(),
            gradleReader = get(),
            sourceDir = File(androidContext().cacheDir, "build-sources"),
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
        )
    }
}
