package com.ahmadkharfan.androidstudiolite.di

import com.ahmadkharfan.androidstudiolite.data.templates.AssetGradleWrapperSource
import com.ahmadkharfan.androidstudiolite.data.templates.ProjectTemplateEngine
import com.ahmadkharfan.androidstudiolite.data.templates.RealTemplateRepository
import com.ahmadkharfan.androidstudiolite.data.templates.TemplateRegistry
import com.ahmadkharfan.androidstudiolite.domain.repository.TemplateRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Real template engine + repository (task T4), replacing the `FakeTemplateRepository` binding that
 * used to live in [dataModule]. Registered from [com.ahmadkharfan.androidstudiolite.AslApplication]
 * after [appModules] so its [TemplateRepository] binding overrides the fake — kept in its own file so
 * it never collides with sibling task edits to the shared [dataModule].
 *
 * The [ProjectTemplateEngine] is consumed by `AndroidProjectRepository` (bound in [localDataModule]);
 * both resolve the same [TemplateRegistry], so the picker and the generator can't drift.
 */
val templatesModule = module {
    single { TemplateRegistry() }
    single { ProjectTemplateEngine(get(), AssetGradleWrapperSource(androidContext().assets)) }
    single<TemplateRepository> { RealTemplateRepository(get()) }
}
