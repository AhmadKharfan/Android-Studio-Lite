package com.ahmadkharfan.androidstudiolite

import android.app.Application
import com.ahmadkharfan.androidstudiolite.di.appModules
import com.ahmadkharfan.androidstudiolite.di.flavorModule
import com.ahmadkharfan.androidstudiolite.di.preferencesModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class AslApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@AslApplication)
            // flavorModule comes from src/play or src/full and selects the flavor-specific
            // bindings (e.g. which BuildSystem implementation backs the build UI).
            modules(appModules + preferencesModule + flavorModule)
        }
    }
}
