package com.hopa.backend.pairing

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * The pending-admission QR a PC shows to a phone:
 * `hopa://pair?v=1&s=<secret>&u=<gateway>`. It carries no household: the secret
 * names a session that a signed-in phone binds to its own household when it
 * vouches. This is deliberately a different type from the operator link
 * ([PairingLink]), and an `h` parameter is refused rather than ignored.
 */
data class AdmissionLink(
    val session: String,
    val gatewayUrl: String,
)

object AdmissionLinks {
    fun parse(text: String): AdmissionLink? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(URI_PREFIX, ignoreCase = true)) return null
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return null
        if (uri.scheme?.equals(SCHEME, ignoreCase = true) != true) return null
        if (uri.host?.equals(PAIR_HOST, ignoreCase = true) != true) return null
        val query = parameters(uri.rawQuery ?: return null)
        if (query["v"] != VERSION) return null
        if (query.containsKey("h")) return null
        val session = query["s"]?.takeIf { it.isNotBlank() } ?: return null
        val gateway = query["u"]?.takeIf { it.isNotBlank() } ?: return null
        return AdmissionLink(session = session, gatewayUrl = gateway)
    }

    fun encode(link: AdmissionLink): String {
        val session = encode(link.session)
        val gateway = encode(link.gatewayUrl)
        return "$URI_PREFIX$PAIR_HOST?v=$VERSION&s=$session&u=$gateway"
    }

    private fun parameters(rawQuery: String): Map<String, String> = rawQuery
        .split('&')
        .mapNotNull { entry ->
            val separator = entry.indexOf('=')
            if (separator <= 0) return@mapNotNull null
            decode(entry.substring(0, separator)) to decode(entry.substring(separator + 1))
        }
        .toMap()

    private fun decode(value: String): String =
        runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8) }.getOrDefault(value)

    /** Query-component encoding; `+` is a literal plus in a URI query, not a space. */
    private fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private const val URI_PREFIX = "hopa://"
    private const val SCHEME = "hopa"
    private const val PAIR_HOST = "pair"
    private const val VERSION = "1"
}
