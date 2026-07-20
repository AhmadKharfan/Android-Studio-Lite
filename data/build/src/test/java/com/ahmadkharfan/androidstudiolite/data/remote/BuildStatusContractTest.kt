package com.ahmadkharfan.androidstudiolite.data.remote

import com.ahmadkharfan.androidstudiolite.data.remote.protocol.BuildStatusResponse
import com.ahmadkharfan.androidstudiolite.data.remote.protocol.RemoteJson
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Test

class BuildStatusContractTest {

    @Test
    fun `decodes the control plane terminal status contract`() {
        val status = RemoteJson.decodeFromString<BuildStatusResponse>(
            """{"buildId":"b1","status":"FAILED","createdAt":"2026-07-20T10:00:00Z","startedAt":"2026-07-20T10:00:01Z","finishedAt":"2026-07-20T10:00:02Z","durationMillis":1000,"artifactUrl":null,"errorMessage":"wrong password","problems":[]}""",
        )

        assertEquals("FAILED", status.status)
        assertEquals("wrong password", status.errorMessage)
        assertEquals("2026-07-20T10:00:01Z", status.startedAt)
    }
}
