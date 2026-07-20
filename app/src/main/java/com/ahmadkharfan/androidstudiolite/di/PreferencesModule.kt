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

val preferencesModule = module {
    single<PreferencesRepository> { DataStorePreferencesRepository(androidContext().appPreferencesDataStore) }
}
