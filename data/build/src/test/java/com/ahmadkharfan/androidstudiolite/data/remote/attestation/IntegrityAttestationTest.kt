package com.ahmadkharfan.androidstudiolite.data.remote.attestation

import com.ahmadkharfan.androidstudiolite.data.remote.protocol.RegisterDeviceRequest
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.RemoteJson
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntegrityAttestationTest {

    private class FakeIntegrityTokenProvider(private val token: String?) : IntegrityTokenProvider {
        var lastNonce: String? = null
        override suspend fun requestToken(nonce: String): String? {
            lastNonce = nonce
            return token
        }
    }

    @Test
    fun `noop provider never attests`() = runTest {
        assertNull(NoopIntegrityTokenProvider.requestToken("nonce"))
    }

    @Test
    fun `mocked token is requested with the given nonce`() = runTest {
        val provider = FakeIntegrityTokenProvider("integrity-abc")
        val token = provider.requestToken("nonce-123")
        assertEquals("integrity-abc", token)
        assertEquals("nonce-123", provider.lastNonce)
    }

    @Test
    fun `register request serializes the token under the server field name`() {
        val json = RemoteJson.encodeToString(RegisterDeviceRequest(integrityToken = "integrity-abc"))
        assertTrue(json, json.contains("\"integrityToken\":\"integrity-abc\""))
    }

    @Test
    fun `register request omits the token when attestation is unavailable`() {

        val json = RemoteJson.encodeToString(RegisterDeviceRequest(integrityToken = null))
        assertFalse(json, json.contains("integrityToken"))
    }
}
