package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.data.templates.AssetGradleWrapperSource
import com.ahmadkharfan.androidstudiolite.data.templates.ProjectTemplateEngine
import com.ahmadkharfan.androidstudiolite.data.templates.RealTemplateRepository
import com.ahmadkharfan.androidstudiolite.data.templates.TemplateRegistry
import com.ahmadkharfan.androidstudiolite.domain.repository.TemplateRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

val templatesModule = module {
    single { TemplateRegistry() }
    single { ProjectTemplateEngine(get(), AssetGradleWrapperSource(androidContext().assets)) }
    single<TemplateRepository> { RealTemplateRepository(get()) }
}
