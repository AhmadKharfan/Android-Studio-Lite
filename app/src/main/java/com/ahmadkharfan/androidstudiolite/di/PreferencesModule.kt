package com.ahmadkharfan.androidstudiolite.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.ahmadkharfan.androidstudiolite.data.local.DataStorePreferencesRepository
import com.ahmadkharfan.androidstudiolite.domain.repository.PreferencesRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

private val Context.appPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(name = "app_preferences")

/**
 * Binds the real DataStore-backed [PreferencesRepository] so settings survive process death.
 * Registered from [com.ahmadkharfan.androidstudiolite.AslApplication] alongside [appModules].
 */
val preferencesModule = module {
    single<PreferencesRepository> { DataStorePreferencesRepository(androidContext().appPreferencesDataStore) }
}
