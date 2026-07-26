package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.data.remote.protocol.BuildStatusResponse
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.CreateBuildRequest
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.CreateBuildResponse
import java.io.File
import okhttp3.WebSocket
import okhttp3.WebSocketListener

internal interface RemoteBuildGateway {
    suspend fun requireSecureSigningTransport()
    suspend fun createBuild(request: CreateBuildRequest): CreateBuildResponse
    suspend fun uploadSource(uploadUrl: String, file: File, method: String = "PUT")
    suspend fun startBuild(buildId: String)
    suspend fun cancelBuild(buildId: String)
    suspend fun buildStatus(buildId: String): BuildStatusResponse
    suspend fun openStream(buildId: String, listener: WebSocketListener): WebSocket
}
