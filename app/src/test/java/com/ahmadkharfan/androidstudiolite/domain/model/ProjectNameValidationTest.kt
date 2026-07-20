package com.ahmadkharfan.androidstudiolite.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectNameValidationTest {

    private fun invalidReason(pkg: String): String? =
        (validatePackageName(pkg) as? ProjectNameValidation.Invalid)?.reason

    @Test
    fun `accepts ordinary application ids`() {
        for (pkg in listOf("com.example.myapp", "io.acme.weather_pro", "a.b", "com.example.app2")) {
            assertEquals(pkg, ProjectNameValidation.Valid, validatePackageName(pkg))
        }
    }

    @Test
    fun `requires at least two segments`() {
        assertTrue(invalidReason("myapp")!!.contains("two parts"))
    }

    @Test
    fun `rejects uppercase`() {
        assertTrue(invalidReason("com.Example.App")!!.contains("lowercase"))
    }

    @Test
    fun `rejects segments that do not start with a letter`() {
        assertTrue(invalidReason("com.2fast")!!.contains("start with a letter"))
        assertTrue(invalidReason("com._private")!!.contains("start with a letter"))
    }

    @Test
    fun `rejects illegal characters and empty segments`() {
        assertTrue(invalidReason("com.exa-mple.app")!!.contains("letters, digits or _"))
        assertTrue(invalidReason("com..app")!!.contains("empty part"))
        assertTrue(invalidReason("com.my app")!!.contains("spaces"))
        assertTrue(invalidReason("")!!.contains("empty"))
    }

    @Test
    fun `rejects java keywords`() {
        assertTrue(invalidReason("com.example.class")!!.contains("reserved"))
        assertTrue(invalidReason("com.new.app")!!.contains("reserved"))
    }
}
