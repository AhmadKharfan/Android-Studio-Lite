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
 * Build / install / run wiring for the flavor-agnostic UI (T11). Everything here targets only the
 * [BuildSystem] interface, so it works unchanged once the real per-flavor backends bind it.
 *
 * TEMPORARY: [BuildSystem] is bound to [FakeBuildSystem] here so the whole build → install → run UI is
 * runnable and demonstrable before T9 (play `InProcessBuildSystem`) and T10 (full
 * `GradleToolingBuildSystem`) land. Each flavor's Koin module (`src/play` / `src/full`
 * `FlavorModule.kt`) will bind the real `BuildSystem`; when it does, REMOVE the `BuildSystem` binding
 * below (Koin would otherwise have a duplicate definition). The installer, keystore manager,
 * notifier, and coordinator are permanent and flavor-neutral.
 */
val buildRunModule = module {
    // TEMPORARY — remove once T9/T10 bind BuildSystem per-flavor in FlavorModule.kt.
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
