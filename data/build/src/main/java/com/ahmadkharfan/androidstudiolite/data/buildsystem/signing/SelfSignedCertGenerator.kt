package com.ahmadkharfan.androidstudiolite.data.buildsystem.signing

import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.SecureRandom
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date
import java.util.TimeZone

internal object SelfSignedCertGenerator {

    private const val OID_SHA256_RSA = "1.2.840.113549.1.1.11"

    private const val TAG_GENERALIZED_TIME = 0x18

    private val ATTRIBUTE_OIDS = mapOf(
        "CN" to "2.5.4.3",
        "OU" to "2.5.4.11",
        "O" to "2.5.4.10",
        "L" to "2.5.4.7",
        "ST" to "2.5.4.8",
        "S" to "2.5.4.8",
        "C" to "2.5.4.6",
        "STREET" to "2.5.4.9",
        "DC" to "0.9.2342.19200300.100.1.25",
        "UID" to "0.9.2342.19200300.100.1.1",
    )

    fun generate(keyPair: KeyPair, dname: String, validityDays: Int): X509Certificate {
        val name = encodeName(dname)
        val algId = DerWriter.sequence(DerWriter.oid(OID_SHA256_RSA), DerWriter.nullValue())

        val now = System.currentTimeMillis()
        val notBefore = Date(now - 60_000L)
        val notAfter = Date(now + validityDays.toLong() * 24 * 60 * 60 * 1000)

        val serial = BigInteger(64, SecureRandom()).abs().let { if (it.signum() == 0) BigInteger.ONE else it }
        val tbs = DerWriter.sequence(
            DerWriter.explicitContext0(DerWriter.integer(2)),
            DerWriter.integer(serial),
            algId,
            name,
            DerWriter.sequence(encodeTime(notBefore), encodeTime(notAfter)),
            name,
            DerWriter.raw(keyPair.public.encoded),
        )

        val signature = Signature.getInstance("SHA256withRSA").run {
            initSign(keyPair.private)
            update(tbs)
            sign()
        }

        val certDer = DerWriter.sequence(tbs, algId, DerWriter.bitString(signature))
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(certDer)) as X509Certificate
    }

    private fun encodeName(dname: String): ByteArray {
        val rdns = splitDname(dname).map { (key, value) ->
            val oid = ATTRIBUTE_OIDS[key.uppercase()]
                ?: throw IllegalArgumentException("Unsupported DN attribute: $key")
            DerWriter.set(DerWriter.sequence(DerWriter.oid(oid), DerWriter.utf8String(value)))
        }
        return DerWriter.tlv(DerWriter.TAG_SEQUENCE, rdns.fold(ByteArray(0)) { acc, e -> acc + e })
    }

    private fun splitDname(dname: String): List<Pair<String, String>> {
        val pairs = mutableListOf<Pair<String, String>>()
        val current = StringBuilder()
        var i = 0
        while (i < dname.length) {
            val c = dname[i]
            if (c == '\\' && i + 1 < dname.length) {
                current.append(dname[i + 1]); i += 2; continue
            }
            if (c == ',') {
                pairs.addRdn(current.toString()); current.setLength(0); i++; continue
            }
            current.append(c); i++
        }
        pairs.addRdn(current.toString())
        require(pairs.isNotEmpty()) { "Empty distinguished name" }
        return pairs
    }

    private fun MutableList<Pair<String, String>>.addRdn(segment: String) {
        val trimmed = segment.trim()
        if (trimmed.isEmpty()) return
        val eq = trimmed.indexOf('=')
        require(eq > 0) { "Malformed DN segment: '$segment'" }
        add(trimmed.substring(0, eq).trim() to trimmed.substring(eq + 1).trim())
    }

    private fun encodeTime(date: Date): ByteArray {
        val utc = TimeZone.getTimeZone("UTC")
        val year = java.util.Calendar.getInstance(utc).apply { time = date }.get(java.util.Calendar.YEAR)
        return if (year in 1950..2049) {
            DerWriter.utcTime(format(date, "yyMMddHHmmss") + "Z")
        } else {
            DerWriter.tlv(TAG_GENERALIZED_TIME, (format(date, "yyyyMMddHHmmss") + "Z").toByteArray(Charsets.US_ASCII))
        }
    }

    private fun format(date: Date, pattern: String): String =
        java.text.SimpleDateFormat(pattern, java.util.Locale.US)
            .apply { timeZone = TimeZone.getTimeZone("UTC") }
            .format(date)
}
