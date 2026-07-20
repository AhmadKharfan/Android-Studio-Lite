package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.data.gradle.GradleProjectReader
import com.ahmadkharfan.androidstudiolite.data.gradle.deps.DependencyManager
import com.ahmadkharfan.androidstudiolite.data.gradle.deps.MavenDependencySearch
import org.koin.dsl.module

val gradleModule = module {
    single { GradleProjectReader() }
    single { DependencyManager() }
    single { MavenDependencySearch() }
}
