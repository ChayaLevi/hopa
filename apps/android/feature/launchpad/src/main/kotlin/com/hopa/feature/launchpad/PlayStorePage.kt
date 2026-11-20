package com.hopa.feature.launchpad

import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The Play Store page for a package, and a cheap answer to "does it have one".
 *
 * Play answers 404 for a package it does not know, and 200 for one it does, so
 * a HEAD request is enough. Anything else - offline, a blocked network, a
 * redirect to a consent page - is "do not know": the store page is opened and
 * says the truth itself.
 */
object PlayStorePage {
    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+")

    /** The page for [packageName], or null when the string is not a package. */
    fun url(packageName: String): String? {
        val id = packageName.trim()
        if (!PACKAGE.matches(id)) {
            return null
        }
        return PAGE + URLEncoder.encode(id, "UTF-8")
    }

    /** true when the page exists, false when Play says 404, null when unsure. */
    suspend fun exists(packageName: String): Boolean? = withContext(Dispatchers.IO) {
        val link = url(packageName) ?: return@withContext null
        val code = runCatching {
            val connection = URL(link).openConnection() as HttpURLConnection
            connection.requestMethod = "HEAD"
            connection.instanceFollowRedirects = true
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
            try {
                connection.responseCode
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
        when {
            code == null -> null
            code == 404 -> false
            code in 200..399 -> true
            else -> null
        }
    }

    private const val PAGE = "https://play.google.com/store/apps/details?id="
    private const val TIMEOUT_MS = 4_000
}
