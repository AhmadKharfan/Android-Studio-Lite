package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.data.gradle.deps.DependencyManager
import com.ahmadkharfan.androidstudiolite.data.gradle.deps.MavenDependencySearch
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.GradleProjectInspector
import org.koin.dsl.module

val gradleModule = module {
    single { GradleProjectReader() }

    // Features depend on the narrow domain contract; the data layer keeps the richer reader for its
    // own use, so both resolve to the same instance rather than two parsers.
    single<GradleProjectInspector> { get<GradleProjectReader>() }
    single { DependencyManager() }
    single { MavenDependencySearch() }
}
