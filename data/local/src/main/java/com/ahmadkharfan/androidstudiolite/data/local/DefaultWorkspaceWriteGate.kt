package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.repository.WorkspaceWriteGate
import com.ahmadkharfan.androidstudiolite.domain.repository.WorkspaceWriteHandler
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class DefaultWorkspaceWriteGate : WorkspaceWriteGate {
    private val handlers = ConcurrentHashMap<String, WorkspaceWriteHandler>()

    override suspend fun prepareForWorktreeMutation(root: File) {
        handlers[key(root)]?.prepare()
    }

    override fun register(root: File, handler: WorkspaceWriteHandler): Closeable {
        val key = key(root)
        handlers[key] = handler
        return Closeable { handlers.remove(key, handler) }
    }

    private fun key(root: File): String = runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)
}
