package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.core.tooling.GradleToolingBuildSystem
import com.ahmadkharfan.androidstudiolite.core.tooling.ToolingServerClient
import com.ahmadkharfan.androidstudiolite.core.tooling.ToolingServerLauncher
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Full-flavor bindings: BuildSystem → [GradleToolingBuildSystem], a client to the on-device Gradle
 * tooling server (real Gradle/AGP in a separate process). All UI targets only the [BuildSystem]
 * interface; this binding is what makes the full flavor a true on-device Gradle build.
 */
val flavorModule: Module = module {
    single { ToolingServerLauncher(androidContext()) }
    single { ToolingServerClient(androidContext()) }
    single<BuildSystem> { GradleToolingBuildSystem(client = get(), launcher = get()) }
}
