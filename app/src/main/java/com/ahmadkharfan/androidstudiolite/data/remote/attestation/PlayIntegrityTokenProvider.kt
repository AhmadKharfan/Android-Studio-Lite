package com.ahmadkharfan.androidstudiolite.data.remote.attestation

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.StandardIntegrityManager
import com.google.android.play.core.integrity.StandardIntegrityManager.PrepareIntegrityTokenRequest
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenProvider
import com.google.android.play.core.integrity.StandardIntegrityManager.StandardIntegrityTokenRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Play Integrity [IntegrityTokenProvider] using the **Standard** API for low latency: the token
 * provider is warmed up once (`prepareIntegrityToken`) and reused for every registration. Bound only
 * when `BuildConfig.PLAY_INTEGRITY_ENABLED` is true and a real cloud project number is configured.
 *
 * Fails soft: any Play Services / network / configuration error is swallowed and returns `null`, so a
 * device without Play Integrity can still register (the server accepts unattested tokens while
 * attestation is not required). See the server repo's `docs/attestation.md`.
 */
class PlayIntegrityTokenProvider(
    context: Context,
    private val cloudProjectNumber: Long,
) : IntegrityTokenProvider {

    private val manager: StandardIntegrityManager =
        IntegrityManagerFactory.createStandard(context.applicationContext)

    /** Warmed-up token provider, prepared lazily on first use and reused thereafter. */
    @Volatile private var tokenProvider: StandardIntegrityTokenProvider? = null

    override suspend fun requestToken(nonce: String): String? = try {
        val provider = tokenProvider ?: prepare().also { tokenProvider = it }
        val request = StandardIntegrityTokenRequest.builder().setRequestHash(nonce).build()
        provider.request(request).await().token()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        // Best-effort: a missing/old Play Services, an unlinked project, or no network must not block
        // registration. Reset the warmed provider so the next attempt re-prepares.
        tokenProvider = null
        Log.w(TAG, "Play Integrity token request failed; registering without attestation", e)
        null
    }

    private suspend fun prepare(): StandardIntegrityTokenProvider {
        val request = PrepareIntegrityTokenRequest.builder()
            .setCloudProjectNumber(cloudProjectNumber)
            .build()
        return manager.prepareIntegrityToken(request).await()
    }

    private companion object {
        const val TAG = "PlayIntegrity"
    }
}

/** Suspends until a Play Services [Task] completes, mapping success/failure/cancellation to coroutines. */
private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { error -> cont.resumeWithException(error) }
    addOnCanceledListener { cont.cancel() }
}
