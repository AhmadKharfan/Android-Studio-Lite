package com.ahmadkharfan.androidstudiolite.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Play-flavor bindings. Will bind BuildSystem → InProcessBuildSystem (the on-ART build engine)
 * once :build:engine has an implementation.
 */
val flavorModule: Module = module {
}
