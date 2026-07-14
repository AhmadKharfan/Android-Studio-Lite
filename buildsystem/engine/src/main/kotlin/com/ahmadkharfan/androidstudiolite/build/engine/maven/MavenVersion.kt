package com.ahmadkharfan.androidstudiolite.build.engine.maven

/**
 * A tolerant Maven-ish version comparator good enough for newest-wins conflict resolution. Splits a
 * version into numeric and qualifier tokens and compares token-by-token: numbers numerically, and a
 * release ("") ranks above pre-release qualifiers (alpha/beta/rc/snapshot). Not a byte-perfect clone
 * of Maven's `ComparableVersion`, but stable and monotonic, which is all the resolver needs.
 */
object MavenVersion : Comparator<String> {

    override fun compare(a: String, b: String): Int {
        val ta = tokenize(a)
        val tb = tokenize(b)
        val n = maxOf(ta.size, tb.size)
        for (i in 0 until n) {
            val x = ta.getOrElse(i) { Token.ZERO }
            val y = tb.getOrElse(i) { Token.ZERO }
            val c = x.compareTo(y)
            if (c != 0) return c
        }
        return 0
    }

    fun isNewer(candidate: String, current: String): Boolean = compare(candidate, current) > 0

    private fun tokenize(version: String): List<Token> {
        // Split on '.', '-', '_', '+' and at digit/letter boundaries.
        val normalized = version.replace('_', '.').replace('-', '.').replace('+', '.')
        val tokens = ArrayList<Token>()
        for (part in normalized.split('.')) {
            if (part.isEmpty()) continue
            var buf = StringBuilder()
            var bufDigit = part[0].isDigit()
            for (ch in part) {
                val isDigit = ch.isDigit()
                if (isDigit != bufDigit && buf.isNotEmpty()) {
                    tokens += Token.of(buf.toString(), bufDigit)
                    buf = StringBuilder()
                }
                bufDigit = isDigit
                buf.append(ch)
            }
            if (buf.isNotEmpty()) tokens += Token.of(buf.toString(), bufDigit)
        }
        return tokens
    }

    private class Token private constructor(
        private val numeric: Long?,
        private val qualifierRank: Int,
        private val text: String,
    ) : Comparable<Token> {

        override fun compareTo(other: Token): Int {
            // Numeric tokens outrank qualifier tokens (1.0.1 > 1.0.rc).
            if (numeric != null && other.numeric != null) return numeric.compareTo(other.numeric)
            if (numeric != null) return 1
            if (other.numeric != null) return -1
            val r = qualifierRank.compareTo(other.qualifierRank)
            return if (r != 0) r else text.compareTo(other.text)
        }

        companion object {
            val ZERO = Token(0, 0, "")

            fun of(value: String, isDigit: Boolean): Token =
                if (isDigit) Token(value.toLongOrNull() ?: 0L, 0, value)
                else Token(null, qualifierRank(value), value.lowercase())

            // Lower rank sorts earlier (older). Release outranks all pre-release qualifiers.
            private fun qualifierRank(q: String): Int = when (q.lowercase()) {
                "snapshot" -> -5
                "alpha", "a" -> -4
                "beta", "b" -> -3
                "milestone", "m" -> -2
                "rc", "cr" -> -1
                "", "ga", "final", "release" -> 0
                "sp" -> 1
                else -> 0
            }
        }
    }
}
