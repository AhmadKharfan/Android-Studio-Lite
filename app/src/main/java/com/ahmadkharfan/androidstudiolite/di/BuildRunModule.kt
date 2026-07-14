package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.data.buildsystem.FakeBuildSystem
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.ApkInstaller
import com.ahmadkharfan.androidstudiolite.data.buildsystem.signing.AndroidKeystoreManager
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreManager
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildNotifier
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildRunCoordinator
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Build / install / run wiring for the build UI (T11). Everything here targets only the [BuildSystem]
 * interface. The app now builds server-side, but that backend isn't wired yet, so [BuildSystem] is
 * temporarily bound to [FakeBuildSystem] — the build console still opens and streams a scripted
 * placeholder. A2 replaces this single binding with `RemoteBuildSystem`; the installer, keystore
 * manager, notifier, and coordinator around it are permanent.
 */
val buildRunModule = module {
    single<BuildSystem> { FakeBuildSystem() }
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
