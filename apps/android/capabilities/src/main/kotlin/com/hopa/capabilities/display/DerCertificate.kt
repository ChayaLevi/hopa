package com.hopa.capabilities.display

import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.Signature
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Date

/**
 * A self-signed X.509 certificate, written by hand. adb only needs a certificate
 * that carries the pairing key, so pulling BouncyCastle in for this one job cost
 * megabytes; this is the ASN.1 that job actually needs.
 */
class DerCertificate {
    fun selfSigned(keyPair: KeyPair, commonName: String = "HOPA"): X509Certificate {
        val algorithm = sequence(oid(SHA256_RSA) + der(0x05, ByteArray(0)))
        val name = sequence(
            der(
                0x31,
                sequence(oid(COMMON_NAME) + der(0x0C, commonName.toByteArray(Charsets.UTF_8))),
            ),
        )
        val now = System.currentTimeMillis()
        val validity = sequence(
            utcTime(Date(now - DAY)) + utcTime(Date(now + YEAR)),
        )
        val serial = BigInteger.valueOf(now)
        val version = der(0xA0, der(0x02, byteArrayOf(2)))
        val tbs = sequence(
            version +
                der(0x02, serial.toByteArray()) +
                algorithm +
                name +
                validity +
                name +
                keyPair.public.encoded,
        )
        val signer = Signature.getInstance("SHA256withRSA")
        signer.initSign(keyPair.private)
        signer.update(tbs)
        val signature = signer.sign()
        val certificate = sequence(
            tbs + algorithm + der(0x03, byteArrayOf(0) + signature),
        )
        return CertificateFactory.getInstance("X.509")
            .generateCertificate(certificate.inputStream()) as X509Certificate
    }

    private fun oid(value: String): ByteArray {
        val parts = value.split('.').map { it.toInt() }
        val out = ByteArrayOutputStream()
        out.write(parts[0] * 40 + parts[1])
        for (index in 2 until parts.size) {
            var number = parts[index]
            val stack = ArrayDeque<Byte>()
            stack.addFirst((number and 0x7F).toByte())
            number = number shr 7
            while (number > 0) {
                stack.addFirst(((number and 0x7F) or 0x80).toByte())
                number = number shr 7
            }
            stack.forEach { out.write(it.toInt()) }
        }
        return der(0x06, out.toByteArray())
    }

    private fun sequence(content: ByteArray): ByteArray = der(0x30, content)

    private fun utcTime(date: Date): ByteArray {
        val text = java.text.SimpleDateFormat("yyMMddHHmmss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(date)
        return der(0x17, text.toByteArray(Charsets.US_ASCII))
    }

    private fun der(tag: Int, content: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(tag)
        val length = content.size
        when {
            length < 0x80 -> out.write(length)
            length < 0x100 -> {
                out.write(0x81)
                out.write(length)
            }

            length < 0x10000 -> {
                out.write(0x82)
                out.write(length shr 8)
                out.write(length and 0xFF)
            }

            else -> {
                out.write(0x83)
                out.write(length shr 16)
                out.write((length shr 8) and 0xFF)
                out.write(length and 0xFF)
            }
        }
        out.write(content)
        return out.toByteArray()
    }

    private companion object {
        const val SHA256_RSA = "1.2.840.113549.1.1.11"
        const val COMMON_NAME = "2.5.4.3"
        const val DAY = 24L * 60 * 60 * 1000
        const val YEAR = 365L * DAY
    }
}
