package com.ahmadkharfan.androidstudiolite.data.local

import com.ahmadkharfan.androidstudiolite.domain.model.ActiveOperation
import com.ahmadkharfan.androidstudiolite.domain.model.GitOperationType
import com.ahmadkharfan.androidstudiolite.domain.repository.GitOperationMonitor
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Serializes mutating JGit commands per canonical repository directory. */
class GitOperationCoordinator : GitOperationMonitor {
    private val entries = ConcurrentHashMap<String, Entry>()
    private val nextId = AtomicLong(0)

    override fun activeOperation(repoDir: File): StateFlow<ActiveOperation?> =
        entry(repoDir).active.asStateFlow()

    override fun cancelActiveOperation(repoDir: File): Boolean {
        val entry = entry(repoDir)
        if (entry.active.value?.cancellable != true) return false
        return entry.job?.let {
            it.cancel(CancellationException("Git operation cancelled"))
            true
        } ?: false
    }

    internal suspend fun <T> runExclusive(
        repoDir: File,
        type: GitOperationType,
        cancellable: Boolean = false,
        block: suspend () -> T,
    ): T {
        val entry = entry(repoDir)
        return entry.mutex.withLock {
            val job = currentCoroutineContext()[Job]
            entry.job = job
            entry.active.value = ActiveOperation(
                id = nextId.incrementAndGet(),
                type = type,
                label = type.defaultLabel,
                cancellable = cancellable,
            )
            try {
                block()
            } finally {
                entry.active.value = null
                if (entry.job === job) entry.job = null
            }
        }
    }

    internal fun updateProgress(repoDir: File, fraction: Float?, message: String) {
        val entry = entry(repoDir)
        entry.active.value = entry.active.value?.copy(progress = fraction, message = message)
    }

    internal fun isCancellationRequested(repoDir: File): Boolean = entry(repoDir).job?.isCancelled == true

    private fun entry(repoDir: File): Entry = entries.getOrPut(canonicalGitKey(repoDir)) { Entry() }

    private fun canonicalGitKey(repoDir: File): String {
        val gitDir = File(repoDir, ".git").takeIf { it.exists() } ?: repoDir
        return runCatching { gitDir.canonicalPath }.getOrDefault(gitDir.absolutePath)
    }

    private class Entry(
        val mutex: Mutex = Mutex(),
        val active: MutableStateFlow<ActiveOperation?> = MutableStateFlow(null),
    ) {
        @Volatile var job: Job? = null
    }
}
