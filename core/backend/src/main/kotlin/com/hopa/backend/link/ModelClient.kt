package com.hopa.backend.link

import com.hopa.contracts.json.HopaJson
import com.hopa.inference.local.ModelManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches the on-device model manifest from the backend.
 *
 * A 304, an error, an unpaired phone or a document this build cannot decode is
 * null: the caller keeps the local catalogue. The app sends the version it
 * holds as `If-None-Match`, so an unchanged list costs one request and no body.
 */
class ModelClient(
    private val baseUrl: String,
    private val token: String,
    private val client: OkHttpClient = OkHttpClient(),
) {
    suspend fun manifest(knownVersion: String): ModelManifest? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(baseUrl.trimEnd('/') + "/v1/models")
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
                if (!response.isSuccessful) return@use null
                val body = response.body?.string() ?: return@use null
                HopaJson.element.decodeFromString(ModelManifest.serializer(), body)
            }
        }.getOrNull()
    }
}
