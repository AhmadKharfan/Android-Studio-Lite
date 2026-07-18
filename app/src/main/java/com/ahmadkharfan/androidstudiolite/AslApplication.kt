package com.ahmadkharfan.androidstudiolite

import android.app.Application
import com.ahmadkharfan.androidstudiolite.di.aiModule
import com.ahmadkharfan.androidstudiolite.di.appModules
import com.ahmadkharfan.androidstudiolite.di.buildRunModule
import com.ahmadkharfan.androidstudiolite.di.gitModule
import com.ahmadkharfan.androidstudiolite.di.localDataModule
import com.ahmadkharfan.androidstudiolite.di.preferencesModule
import com.ahmadkharfan.androidstudiolite.di.remoteModule
import com.ahmadkharfan.androidstudiolite.di.templatesModule
import com.ahmadkharfan.androidstudiolite.di.terminalModule
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
            // Single flavor (builds run server-side). buildRunModule binds BuildSystem to
            // RemoteBuildSystem; remoteModule provides its transport, packager, downloader, settings.
            modules(appModules + localDataModule + templatesModule + preferencesModule + terminalModule + gitModule + remoteModule + buildRunModule + aiModule)
        }
    }
}
