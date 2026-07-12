package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.data.gradle.deps.DependencyManager
import com.ahmadkharfan.androidstudiolite.data.gradle.deps.MavenDependencySearch
import org.koin.dsl.module

/**
 * Static Gradle understanding + dependency management (Phase 2 / T8). Registered from
 * [com.ahmadkharfan.androidstudiolite.AslApplication] via [appModules].
 *
 * These types are flavor-neutral: the play flavor's in-process sync (T9) and the full flavor's
 * pre-sync validation both consume [GradleProjectReader], and both flavors' dependency UI uses
 * [DependencyManager] / [MavenDependencySearch].
 */
val gradleModule = module {
    single { GradleProjectReader() }
    single { DependencyManager() }
    single { MavenDependencySearch() }
}
