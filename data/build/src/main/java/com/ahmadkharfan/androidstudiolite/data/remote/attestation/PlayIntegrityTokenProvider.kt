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

class PlayIntegrityTokenProvider(
    context: Context,
    private val cloudProjectNumber: Long,
) : IntegrityTokenProvider {

    private val manager: StandardIntegrityManager =
        IntegrityManagerFactory.createStandard(context.applicationContext)

    @Volatile private var tokenProvider: StandardIntegrityTokenProvider? = null

    override suspend fun requestToken(nonce: String): String? = try {
        val provider = tokenProvider ?: prepare().also { tokenProvider = it }
        val request = StandardIntegrityTokenRequest.builder().setRequestHash(nonce).build()
        provider.request(request).await().token()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {


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

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { result -> cont.resume(result) }
    addOnFailureListener { error -> cont.resumeWithException(error) }
    addOnCanceledListener { cont.cancel() }
}
