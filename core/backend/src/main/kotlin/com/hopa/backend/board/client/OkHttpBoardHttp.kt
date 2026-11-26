package com.hopa.backend.board.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** [BoardHttp] over OkHttp, with the device's access token. */
class OkHttpBoardHttp(
    private val baseUrl: String,
    private val token: suspend () -> String,
    private val client: OkHttpClient = OkHttpClient(),
) : BoardHttp {
    private val json = "application/json".toMediaType()

    override suspend fun get(path: String): HttpAnswer = call(request(path).get())

    override suspend fun post(path: String, json: String): HttpAnswer = call(
        request(path).post(json.toRequestBody(this.json)),
    )

    override suspend fun put(path: String, json: String): HttpAnswer = call(
        request(path).put(json.toRequestBody(this.json)),
    )

    private suspend fun request(path: String): Request.Builder =
        Request.Builder().url(baseUrl.trimEnd('/') + path).header("Authorization", "Bearer ${token()}")

    private suspend fun call(builder: Request.Builder): HttpAnswer = withContext(Dispatchers.IO) {
        client.newCall(builder.build()).execute().use { response ->
            HttpAnswer(response.code, response.body?.string().orEmpty())
        }
    }
}
