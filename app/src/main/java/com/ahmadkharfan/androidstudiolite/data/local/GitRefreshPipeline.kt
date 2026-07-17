package com.ahmadkharfan.androidstudiolite.data.local

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import org.eclipse.jgit.lib.EmptyProgressMonitor
import org.eclipse.jgit.lib.ProgressMonitor

internal enum class GitRefreshKind { DEBOUNCED, IMMEDIATE }

internal data class RefreshRequest(
    val kind: GitRefreshKind,
    val includeIgnored: Boolean,
    val completion: CompletableDeferred<Unit>? = null,
)

/** Coalesces status triggers and cancels a superseded scan through its JGit progress monitor. */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal class GitRefreshPipeline(
    scope: CoroutineScope,
    private val debounceMillis: Long = DEFAULT_DEBOUNCE_MILLIS,
    private val scan: suspend (includeIgnored: Boolean, monitor: ProgressMonitor) -> Unit,
) {
    private val requests = MutableSharedFlow<RefreshRequest>(
        replay = 1,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    init {
        // UNDISTPATCHED starts collector setup immediately; replay=1 also protects the first request
        // while transformLatest installs its internal collector under a test dispatcher/cold start.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            requests
                .transformLatest { request ->
                    if (request.kind == GitRefreshKind.DEBOUNCED) delay(debounceMillis)
                    emit(request)
                }
                .collectLatest { request ->
                    val monitor = CoroutineProgressMonitor(currentCoroutineContext().job)
                    try {
                        scan(request.includeIgnored, monitor)
                        request.completion?.complete(Unit)
                    } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        // A newer request superseded this scan. Its caller only asked for a fresh
                        // state, which the newer scan will provide, so do not turn coalescing into an
                        // application error.
                        request.completion?.complete(Unit)
                        throw cancelled
                    } catch (error: Throwable) {
                        request.completion?.completeExceptionally(error)
                    }
                }
        }
    }

    suspend fun requestImmediate(includeIgnored: Boolean) {
        val completion = CompletableDeferred<Unit>()
        requests.emit(RefreshRequest(GitRefreshKind.IMMEDIATE, includeIgnored, completion))
        completion.await()
    }

    fun requestDebounced(includeIgnored: Boolean) {
        requests.tryEmit(RefreshRequest(GitRefreshKind.DEBOUNCED, includeIgnored))
    }

    private class CoroutineProgressMonitor(private val job: Job) : EmptyProgressMonitor() {
        override fun isCancelled(): Boolean = !job.isActive
    }

    private companion object {
        const val DEFAULT_DEBOUNCE_MILLIS = 300L
    }
}
