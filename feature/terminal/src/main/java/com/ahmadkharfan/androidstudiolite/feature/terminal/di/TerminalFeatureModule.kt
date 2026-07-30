package com.ahmadkharfan.androidstudiolite.feature.terminal.di

import com.ahmadkharfan.androidstudiolite.feature.terminal.TerminalViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val terminalFeatureModule = module {
    viewModelOf(::TerminalViewModel)
}
