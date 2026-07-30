package com.ahmadkharfan.androidstudiolite.feature.terminal.di

import com.ahmadkharfan.androidstudiolite.feature.terminal.TerminalFeatureApiImpl
import com.ahmadkharfan.androidstudiolite.feature.terminal.TerminalViewModel
import com.ahmadkharfan.androidstudiolite.feature.terminal.api.TerminalFeatureApi
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val terminalFeatureModule = module {
    single<TerminalFeatureApi> { TerminalFeatureApiImpl() }
    viewModelOf(::TerminalViewModel)
}
