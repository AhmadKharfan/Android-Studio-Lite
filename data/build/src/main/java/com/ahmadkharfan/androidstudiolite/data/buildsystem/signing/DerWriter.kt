package com.ahmadkharfan.androidstudiolite.data.buildsystem.signing

import java.io.ByteArrayOutputStream
import java.math.BigInteger

internal object DerWriter {

    const val TAG_INTEGER: Int = 0x02
    const val TAG_BIT_STRING: Int = 0x03
    const val TAG_NULL: Int = 0x05
    const val TAG_OID: Int = 0x06
    const val TAG_UTF8_STRING: Int = 0x0C
    const val TAG_PRINTABLE_STRING: Int = 0x13
    const val TAG_UTC_TIME: Int = 0x17
    const val TAG_SEQUENCE: Int = 0x30
    const val TAG_SET: Int = 0x31
    const val TAG_CONTEXT_0: Int = 0xA0

    fun tlv(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        out.write(encodeLength(content.size))
        out.write(content)
        return out.toByteArray()
    }

    fun sequence(vararg parts: ByteArray): ByteArray = tlv(TAG_SEQUENCE, parts.concat())

    fun set(vararg parts: ByteArray): ByteArray = tlv(TAG_SET, parts.concat())

    fun integer(value: BigInteger): ByteArray = tlv(TAG_INTEGER, value.toByteArray())

    fun integer(value: Long): ByteArray = integer(BigInteger.valueOf(value))

    fun raw(bytes: ByteArray): ByteArray = bytes

    fun oid(dotted: String): ByteArray = tlv(TAG_OID, encodeOid(dotted))

    fun nullValue(): ByteArray = tlv(TAG_NULL, ByteArray(0))

    fun bitString(bytes: ByteArray): ByteArray {
        val body = ByteArray(bytes.size + 1)
        body[0] = 0
        System.arraycopy(bytes, 0, body, 1, bytes.size)
        return tlv(TAG_BIT_STRING, body)
    }

    fun utf8String(value: String): ByteArray = tlv(TAG_UTF8_STRING, value.toByteArray(Charsets.UTF_8))

    fun printableString(value: String): ByteArray = tlv(TAG_PRINTABLE_STRING, value.toByteArray(Charsets.US_ASCII))

    fun utcTime(value: String): ByteArray = tlv(TAG_UTC_TIME, value.toByteArray(Charsets.US_ASCII))

    fun explicitContext0(content: ByteArray): ByteArray = tlv(TAG_CONTEXT_0, content)

    private fun encodeLength(length: Int): ByteArray {
        if (length < 0x80) return byteArrayOf(length.toByte())
        val bytes = ByteArrayOutputStream()
        var remaining = length
        val stack = ArrayDeque<Int>()
        while (remaining > 0) {
            stack.addFirst(remaining and 0xFF)
            remaining = remaining ushr 8
        }
        bytes.write(0x80 or stack.size)
        stack.forEach { bytes.write(it) }
        return bytes.toByteArray()
    }

    private fun encodeOid(dotted: String): ByteArray {
        val parts = dotted.split('.').map { it.toLong() }
        require(parts.size >= 2) { "OID needs at least two arcs: $dotted" }
        val out = ByteArrayOutputStream()
        out.write((parts[0] * 40 + parts[1]).toInt())
        for (i in 2 until parts.size) {
            out.write(encodeBase128(parts[i]))
        }
        return out.toByteArray()
    }

    private fun encodeBase128(value: Long): ByteArray {
        if (value == 0L) return byteArrayOf(0)
        val stack = ArrayDeque<Int>()
        var remaining = value
        while (remaining > 0) {
            stack.addFirst((remaining and 0x7F).toInt())
            remaining = remaining ushr 7
        }
        val bytes = ByteArray(stack.size)
        for (i in stack.indices) {

            bytes[i] = (stack[i] or if (i < stack.size - 1) 0x80 else 0x00).toByte()
        }
        return bytes
    }

    private fun Array<out ByteArray>.concat(): ByteArray {
        val out = ByteArrayOutputStream()
        forEach { out.write(it) }
        return out.toByteArray()
    }
}
