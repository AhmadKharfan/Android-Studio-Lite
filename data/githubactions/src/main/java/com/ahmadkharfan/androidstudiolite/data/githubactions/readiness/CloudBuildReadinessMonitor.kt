package com.ahmadkharfan.androidstudiolite.data.githubactions.readiness

import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildReadiness
import com.ahmadkharfan.androidstudiolite.domain.buildsystem.CloudBuildState
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 * Holds the latest Cloud Build readiness and re-checks it when something that affects it changes.
 *
 * Checks run in [scope], so one started for a screen that goes away still completes and updates
 * [state]. Concurrent [check] calls share the running check.
 *
 * @param recheckTriggers each emission re-checks, e.g. the build sign-in changing.
 * @param onlineChanges re-checks when the device comes back online after an [CloudBuildState.Offline] answer.
 */
class CloudBuildReadinessMonitor(
    private val checker: suspend () -> CloudBuildState,
    private val scope: CoroutineScope,
    recheckTriggers: Flow<Unit> = emptyFlow(),
    onlineChanges: Flow<Boolean> = emptyFlow(),
) : CloudBuildReadiness {

    private val current = MutableStateFlow<CloudBuildState?>(null)
    override val state: StateFlow<CloudBuildState?> = current.asStateFlow()

    private val lock = Any()
    private var running: Deferred<CloudBuildState>? = null

    init {
        scope.launch { recheckTriggers.collect { check() } }
        scope.launch {
            onlineChanges.collect { online -> if (online && current.value == CloudBuildState.Offline) check() }
        }
    }

    override suspend fun check(): CloudBuildState {
        val shared = synchronized(lock) {
            running?.takeIf { it.isActive } ?: scope.async { runCheck() }.also { running = it }
        }
        return shared.await()
    }

    private suspend fun runCheck(): CloudBuildState {
        current.value = CloudBuildState.Checking
        val result = try {
            checker()
        } catch (e: CancellationException) {
            throw e
        } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
            // The checker maps every GitHub answer itself; anything else must not leave the state stuck.
            CloudBuildState.Unknown(e.message ?: e.javaClass.simpleName)
        }
        current.value = result
        return result
    }
}
