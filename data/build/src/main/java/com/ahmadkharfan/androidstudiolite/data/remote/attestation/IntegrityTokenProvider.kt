package com.ahmadkharfan.androidstudiolite.data.remote.attestation

/**
 * Obtains a Play Integrity token to attach to device registration (`POST /v1/devices`). Best-effort by
 * contract: implementations return `null` — never throw — when attestation is disabled or Play
 * Services is unavailable, so registration still succeeds (the server ignores a missing token while
 * `PLAY_INTEGRITY_REQUIRED=false`). See the server repo's `docs/attestation.md`.
 */
interface IntegrityTokenProvider {

    /**
     * Requests a Play Integrity token bound to [nonce] (the request hash), or `null` when attestation
     * is off/unavailable. Never throws.
     */
    suspend fun requestToken(nonce: String): String?
}

/** The default: no attestation. Used for dev builds and whenever `PLAY_INTEGRITY_ENABLED` is false. */
object NoopIntegrityTokenProvider : IntegrityTokenProvider {
    override suspend fun requestToken(nonce: String): String? = null
}
