package com.ahmadkharfan.androidstudiolite

import android.app.Application
import com.ahmadkharfan.androidstudiolite.di.allModules
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


            modules(allModules)
        }
    }
}
