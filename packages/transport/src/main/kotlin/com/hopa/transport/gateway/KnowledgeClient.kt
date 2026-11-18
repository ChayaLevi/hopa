package com.hopa.transport.gateway

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches the knowledge bundle from the backend over HTTPS.
 *
 * A 304 or any failure is null: the caller keeps the copy it has. The app sends
 * the version it holds as `If-None-Match`, so an unchanged catalog costs one
 * request and no body.
 */
class KnowledgeClient(
    private val baseUrl: String,
    private val token: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun fetch(knownVersion: String): String? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/knowledge")
            .header("Authorization", "Bearer $token")
            .apply {
                if (knownVersion.isNotBlank()) {
                    header("If-None-Match", "\"$knownVersion\"")
                }
            }
            .get()
            .build()
        runCatching {
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }
}
