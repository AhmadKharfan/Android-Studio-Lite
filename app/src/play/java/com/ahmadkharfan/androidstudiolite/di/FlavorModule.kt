package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.core.environment.IdeEnvironmentPaths
import com.ahmadkharfan.androidstudiolite.data.build.AndroidToolchainProvider
import com.ahmadkharfan.androidstudiolite.data.build.InProcessBuildSystem
import com.ahmadkharfan.androidstudiolite.data.build.ToolchainProvider
import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildSystem
import java.io.File
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Play-flavor bindings: the on-ART in-process build engine. Bound only in this source set, so the
 * `full` flavor's [BuildSystem] (GradleToolingBuildSystem) never collides with it.
 */
val flavorModule: Module = module {
    single<ToolchainProvider> { AndroidToolchainProvider(androidContext()) }

    single<BuildSystem> {
        val context = androidContext()
        InProcessBuildSystem(
            reader = get<GradleProjectReader>(),
            toolchainProvider = get(),
            mavenCacheDir = File(IdeEnvironmentPaths.gradleUserHome(context), "modules-2/files-in-process"),
            buildRootDir = File(IdeEnvironmentPaths.home(context), ".androidstudiolite/build"),
        )
    }
}
