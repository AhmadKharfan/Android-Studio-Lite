package com.ahmadkharfan.androidstudiolite.data.remote.attestation

interface IntegrityTokenProvider {

    suspend fun requestToken(nonce: String): String?
}

object NoopIntegrityTokenProvider : IntegrityTokenProvider {
    override suspend fun requestToken(nonce: String): String? = null
}
