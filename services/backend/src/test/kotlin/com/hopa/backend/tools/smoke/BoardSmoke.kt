package com.hopa.backend.tools.smoke

import com.hopa.backend.harness.FakeDevice
import com.hopa.backend.harness.bodyString
import com.hopa.backend.protocol.Envelope
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.util.Base64
import java.util.UUID
import kotlin.random.Random
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put

/**
 * The family board smoke run (B4.7): N `family` ops appended over a real
 * gateway socket, then pulled back over HTTP, against a deployed instance.
 * Reports p50 and p95 of the append round-trip (op sent to ack received) and
 * of one pull page. The op bodies are random bytes: the server cannot tell
 * them from ciphertext, and no device can decrypt them, so use a test household.
 */
object BoardSmoke {
    private const val FRAME_TIMEOUT_MS = 60_000L

    class Result(val appendMs: List<Long>, val pullMs: List<Long>, val pulled: Int) {
        val appendP50: Long get() = percentile(appendMs, 50)
        val appendP95: Long get() = percentile(appendMs, 95)
        val pullP50: Long get() = percentile(pullMs, 50)
        val pullP95: Long get() = percentile(pullMs, 95)
    }

    /**
     * [baseUrl] is `https://host` (or `http://`); the gateway is the same host on
     * `wss://`. [paceMs] keeps the run under the gateway's 20 messages a second.
     */
    fun run(baseUrl: String, token: String, ops: Int = 100, pageSize: Int = 50, paceMs: Long = 60): Result = runBlocking {
        val base = baseUrl.trimEnd('/')
        val http = HttpClient(CIO) { install(WebSockets) }
        try {
            suspend fun json(path: String) = http.get("$base$path") { header("Authorization", "Bearer $token") }.let { response ->
                check(response.status.isSuccess()) { "$path answered ${response.status}: ${response.bodyAsText()}" }
                Json.parseToJsonElement(response.bodyAsText()).jsonObject
            }

            val start = json("/v1/board/head")["heads"]!!.jsonObject["family"]?.jsonPrimitive?.long ?: 0L
            val device = FakeDevice("board-smoke", token)
            device.connect(http, base.replaceFirst("http", "ws") + "/v1/gateway")
            device.send(hello())
            device.expect("device.hello.ack", FRAME_TIMEOUT_MS)

            val appendMs = List(ops) { index ->
                val sent = System.nanoTime()
                device.send(op())
                val ack = runCatching { device.expect("board.op.ack", FRAME_TIMEOUT_MS) }
                    .getOrElse { error("op ${index + 1}: no ack (${it.message})") }
                checkNotNull(ack.bodyString("seq")) { "op ${index + 1}: the ack carried no seq" }
                val elapsed = (System.nanoTime() - sent) / 1_000_000
                delay(paceMs)
                elapsed
            }
            device.close()

            val pullMs = mutableListOf<Long>()
            var after = start
            var pulled = 0
            do {
                val sent = System.nanoTime()
                val page = json("/v1/board/ops?scope=family&after=$after&limit=$pageSize")
                pullMs += (System.nanoTime() - sent) / 1_000_000
                val seqs = page["ops"]!!.jsonArray.map { it.jsonObject["seq"]!!.jsonPrimitive.long }
                pulled += seqs.size
                after = seqs.lastOrNull() ?: after
            } while (page["hasMore"]!!.jsonPrimitive.boolean)
            check(pulled >= ops) { "appended $ops ops but pulled $pulled" }
            Result(appendMs, pullMs, pulled)
        } finally {
            http.close()
        }
    }

    fun percentile(values: List<Long>, p: Int): Long {
        if (values.isEmpty()) return 0
        val sorted = values.sorted()
        val rank = Math.ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    private fun hello() = Envelope(
        v = 1,
        id = UUID.randomUUID().toString(),
        type = "device.hello",
        ts = System.currentTimeMillis(),
        body = buildJsonObject {
            put("platform", "android")
            put("name", "board-smoke")
            put("appVersion", "0.1.0")
        },
    )

    private fun op() = Envelope(
        v = 1,
        id = UUID.randomUUID().toString(),
        type = "board.op",
        ts = System.currentTimeMillis(),
        body = buildJsonObject {
            put("scope", "family")
            put("ct", Base64.getEncoder().encodeToString(Random.nextBytes(256)))
            put("keyEpoch", 0)
        },
    )
}

fun main(args: Array<String>) {
    val values = args.toList().chunked(2)
        .filter { it.size == 2 }
        .associate { it[0].removePrefix("--") to it[1] }
    val url = values["url"] ?: error("usage: boardSmoke --url https://... --token <device access token> [--ops 100]")
    val token = values["token"] ?: error("--token is required")
    val ops = values["ops"]?.toIntOrNull() ?: 100

    val result = BoardSmoke.run(url, token, ops)
    println("appended ${result.appendMs.size} ops, pulled ${result.pulled} in ${result.pullMs.size} page(s)")
    println("append round-trip p50=${result.appendP50} ms p95=${result.appendP95} ms")
    println("pull page         p50=${result.pullP50} ms p95=${result.pullP95} ms")
}
