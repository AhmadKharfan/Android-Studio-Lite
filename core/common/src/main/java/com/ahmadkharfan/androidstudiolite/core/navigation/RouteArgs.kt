package com.ahmadkharfan.androidstudiolite.core.navigation

private const val UNRESERVED_PUNCTUATION = "-_.~"
private const val HEX_DIGITS = "0123456789ABCDEF"
private const val BYTE_MASK = 0xFF
private const val HIGH_NIBBLE_SHIFT = 4
private const val LOW_NIBBLE_MASK = 0x0F

/**
 * Percent-encodes [value] so it can be embedded in a route without altering the route's structure.
 *
 * Project ids are directory names, which may legally contain `?`, `#` and `&`. Left raw, those turn
 * a path segment into a query or fragment, so the destination either fails to match or receives a
 * truncated argument.
 *
 * Everything outside the RFC 3986 unreserved set is escaped as UTF-8 percent triplets. Navigation
 * runs matched path and query arguments through `Uri.decode` before handing them to a destination,
 * which reverses this exactly — so read-back sites must not decode again.
 *
 * Deliberately pure Kotlin rather than `android.net.Uri.encode`: route building then stays testable
 * in plain JVM unit tests, where `testOptions.unitTests.isReturnDefaultValues` makes framework calls
 * return null. It escapes a slightly wider set than `Uri.encode` (which leaves `!'()*` alone); both
 * decode back to the same string.
 */
fun encodeRouteArg(value: String): String {
    if (value.all(::isUnreserved)) return value
    return buildString(value.length) {
        for (byte in value.toByteArray(Charsets.UTF_8)) {
            val code = byte.toInt() and BYTE_MASK
            val char = code.toChar()
            if (isUnreserved(char)) {
                append(char)
            } else {
                append('%')
                append(HEX_DIGITS[code shr HIGH_NIBBLE_SHIFT])
                append(HEX_DIGITS[code and LOW_NIBBLE_MASK])
            }
        }
    }
}

private fun isUnreserved(char: Char): Boolean =
    char in 'A'..'Z' || char in 'a'..'z' || char in '0'..'9' || char in UNRESERVED_PUNCTUATION
