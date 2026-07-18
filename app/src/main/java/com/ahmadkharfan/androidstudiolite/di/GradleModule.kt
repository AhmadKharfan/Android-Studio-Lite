package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.data.gradle.deps.DependencyManager
import com.ahmadkharfan.androidstudiolite.data.gradle.deps.MavenDependencySearch
import org.koin.dsl.module

/**
 * Static Gradle understanding + dependency management. Registered from
 * [com.ahmadkharfan.androidstudiolite.AslApplication] via [appModules].
 *
 * These read the project straight off disk (no Gradle execution): the editor's symbol index and the
 * build preflight consume [GradleProjectReader], and the dependency UI uses
 * [DependencyManager] / [MavenDependencySearch].
 */
val gradleModule = module {
    single { GradleProjectReader() }
    single { DependencyManager() }
    single { MavenDependencySearch() }
}
