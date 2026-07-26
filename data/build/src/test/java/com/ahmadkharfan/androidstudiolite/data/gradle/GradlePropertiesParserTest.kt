package com.ahmadkharfan.androidstudiolite.data.gradle

import com.ahmadkharfan.androidstudiolite.data.gradle.parse.GradlePropertiesParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradlePropertiesParserTest {

    @Test
    fun parsesKeyValuePairs() {
        val text = """
            # a comment
            org.gradle.jvmargs=-Xmx2048m
            android.useAndroidX = true
            kotlin.code.style : official
        """.trimIndent()
        val props = GradlePropertiesParser.parse(text)
        assertEquals("-Xmx2048m", props["org.gradle.jvmargs"])
        assertEquals("true", props["android.useAndroidX"])
        assertEquals("official", props["kotlin.code.style"])
    }

    @Test
    fun gradleVersionFromWrapperUrl() {
        val props = mapOf(
            "distributionUrl" to "https\\://services.gradle.org/distributions/gradle-8.7-bin.zip",
        )
        assertEquals("8.7", GradlePropertiesParser.gradleVersionFromWrapper(props))
    }

    @Test
    fun gradleVersionFromAllDistribution() {
        val props = mapOf(
            "distributionUrl" to "https://services.gradle.org/distributions/gradle-8.11.1-all.zip",
        )
        assertEquals("8.11.1", GradlePropertiesParser.gradleVersionFromWrapper(props))
    }

    @Test
    fun gradleVersionAbsentReturnsNull() {
        assertNull(GradlePropertiesParser.gradleVersionFromWrapper(emptyMap()))
    }
}
