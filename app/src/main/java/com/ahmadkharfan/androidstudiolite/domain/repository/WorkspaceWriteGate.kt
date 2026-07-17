package com.ahmadkharfan.androidstudiolite.domain.repository

import java.io.Closeable
import java.io.File

fun interface WorkspaceWriteHandler {
    suspend fun prepare()
}

/** Gives the open editor a chance to persist buffers before Git replaces working-tree files. */
interface WorkspaceWriteGate {
    suspend fun prepareForWorktreeMutation(root: File)
    fun register(root: File, handler: WorkspaceWriteHandler): Closeable
}
