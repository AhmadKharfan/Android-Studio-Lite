package com.ahmadkharfan.androidstudiolite.feature.buildrun

import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.InstallEvent
import com.ahmadkharfan.androidstudiolite.data.buildsystem.install.UninstallEvent
import com.ahmadkharfan.androidstudiolite.data.remote.ActiveBuild
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildEvent
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.BuildRequest
import com.ahmadkharfan.androidstudiolite.feature.buildrun.preflight.BuildPreflightResult
import java.io.File
import kotlinx.coroutines.flow.Flow

interface BuildRunApi {
    suspend fun preflight(projectRoot: File): BuildPreflightResult
    suspend fun ensureDebugKeystore()
    fun build(request: BuildRequest, meta: BuildClientMeta): Flow<BuildEvent>
    suspend fun attachIfActive(projectId: String, projectRoot: File, projectName: String): Flow<BuildEvent>?
    fun updateKeepAliveProgress(projectId: String, projectName: String, progress: String)
    fun endKeepAlive()
    suspend fun activeBuildFor(projectId: String): ActiveBuild?
    fun cancel()
    suspend fun resolveApplicationId(projectRoot: File, modulePath: String): String?
    fun install(apk: File, applicationId: String?, autoLaunch: Boolean): Flow<InstallEvent>
    fun uninstall(applicationId: String): Flow<UninstallEvent>
    fun notifyFinished(
        projectName: String,
        success: Boolean,
        durationMillis: Long?,
        projectId: String = "",
        installFollows: Boolean = false,
    )
    fun canPostNotifications(): Boolean
}
