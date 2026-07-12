package com.ahmadkharfan.androidstudiolite.di

import org.koin.core.module.Module
import org.koin.dsl.module

/**
 * Full-flavor bindings. Will bind BuildSystem → GradleToolingBuildSystem (client to the
 * on-device tooling-server process) once :tooling:server has an implementation.
 */
val flavorModule: Module = module {
}
