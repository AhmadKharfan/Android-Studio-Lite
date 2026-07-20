package com.ahmadkharfan.androidstudiolite.domain.repository

import java.io.Closeable
import java.io.File

fun interface WorkspaceWriteHandler {
    suspend fun prepare()
}

interface WorkspaceWriteGate {
    suspend fun prepareForWorktreeMutation(root: File)
    fun register(root: File, handler: WorkspaceWriteHandler): Closeable
}
