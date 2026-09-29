package com.ahmadkharfan.androidstudiolite.data.githubactions.build

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BuildHandleTest {

    @Test
    fun `handles survive encoding with and without a run id`() {
        listOf(
            BuildHandle("octo", "asl-build", "corr-1", null),
            BuildHandle("octo", "asl-build", "corr-1", 1234567890123),
        ).forEach { assertEquals(it, BuildHandle.decode(it.encode())) }
    }

    @Test
    fun `malformed ids are not handles`() {
        listOf(
            "",
            "5f0c1e2a-uuid",
            "v2|octo|asl-build|corr|1",
            "v1|octo|asl-build|corr",
            "v1||asl-build|corr|1",
            "v1|octo|asl-build|corr|notanumber",
        ).forEach { assertNull(it, BuildHandle.decode(it)) }
    }
}
