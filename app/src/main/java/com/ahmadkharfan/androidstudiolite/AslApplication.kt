package com.ahmadkharfan.androidstudiolite

import android.app.Application
import com.ahmadkharfan.androidstudiolite.data.remote.ActiveBuildRepository
import com.ahmadkharfan.androidstudiolite.di.aiModule
import com.ahmadkharfan.androidstudiolite.di.appModules
import com.ahmadkharfan.androidstudiolite.di.buildRunModule
import com.ahmadkharfan.androidstudiolite.di.gitModule
import com.ahmadkharfan.androidstudiolite.di.localDataModule
import com.ahmadkharfan.androidstudiolite.di.preferencesModule
import com.ahmadkharfan.androidstudiolite.di.remoteModule
import com.ahmadkharfan.androidstudiolite.di.templatesModule
import com.ahmadkharfan.androidstudiolite.di.terminalModule
import com.ahmadkharfan.androidstudiolite.feature.buildrun.RemoteBuildKeepAliveService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.get
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level

class AslApplication : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidLogger(Level.ERROR)
            androidContext(this@AslApplication)


            modules(appModules + localDataModule + templatesModule + preferencesModule + terminalModule + gitModule + remoteModule + buildRunModule + aiModule)
        }


        restoreActiveBuildKeepAlive()
    }

    private fun restoreActiveBuildKeepAlive() {
        appScope.launch {
            val store = runCatching { get<ActiveBuildRepository>() }.getOrNull() ?: return@launch
            val active = store.get() ?: return@launch
            val ageMs = System.currentTimeMillis() - active.startedAtEpochMs
            if (ageMs > ACTIVE_BUILD_MAX_AGE_MS) {
                store.clear(active.buildId)
                return@launch
            }
            RemoteBuildKeepAliveService.startBuilding(
                this@AslApplication,
                active.projectId,
                active.projectName,
                "Build still running — open the project to reconnect",
            )
        }
    }

    private companion object {
        const val ACTIVE_BUILD_MAX_AGE_MS = 1_000_000L
    }
}
