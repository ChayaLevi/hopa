package com.hopa.backend.board.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** One HTTP answer: the status and the body text (empty when there is none). */
data class HttpAnswer(
    val status: Int,
    val body: String,
)

/**
 * The board's HTTP calls, behind a port so the link runs against OkHttp in the apps and against an
 * in-process server in tests. Paths are absolute (`/v1/board/ops?...`); the bearer is added here.
 * A network failure throws; a refused call is an answer with its status.
 */
interface BoardHttp {
    suspend fun get(path: String): HttpAnswer

    suspend fun post(path: String, json: String): HttpAnswer

    suspend fun put(path: String, json: String): HttpAnswer
}

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
