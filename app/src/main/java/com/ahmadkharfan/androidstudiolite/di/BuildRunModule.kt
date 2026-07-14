package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.ApkInstaller
import com.ahmadkharfan.androidstudiolite.data.buildsystem.signing.AndroidKeystoreManager
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import com.ahmadkharfan.androidstudiolite.domain.signing.KeystoreManager
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildNotifier
import com.ahmadkharfan.androidstudiolite.feature.buildrun.BuildRunCoordinator
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Build / install / run wiring for the flavor-agnostic UI (T11). Everything here targets only the
 * [BuildSystem] interface, so it works unchanged once the real per-flavor backends bind it.
 *
 * [BuildSystem] itself is bound per-flavor in `src/play`/`src/full` `FlavorModule.kt`
 * (play `InProcessBuildSystem`, full `GradleToolingBuildSystem`), so it is intentionally NOT bound
 * here. The installer, keystore manager, notifier, and coordinator are permanent and flavor-neutral.
 */
val buildRunModule = module {
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
