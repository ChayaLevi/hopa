package com.hopa.backend.tools

import com.hopa.contracts.testing.FakeDevice
import com.hopa.backend.harness.bodyString
import com.hopa.backend.protocol.Envelope
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.websocket.WebSockets
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * The exit check from week1.md section 8: N alternating round-trips between two
 * real sockets. Each round proves the message left the sender, was accepted by
 * the server under one trace, and arrived at the target.
 */
object ExitRun {
    /**
     * How long one frame may take. CI runners share a machine, and a 15 s wait
     * timed out on a single ack under load; 60 s still bounds a hung test
     * without failing a slow runner.
     */
    private const val FRAME_TIMEOUT_MS = 60_000L

    fun run(url: String, tokenA: String, tokenB: String, rounds: Int = 20): List<String> =
        runBlocking {
            val http = HttpClient(CIO) { install(WebSockets) }
            try {
                val a = FakeDevice("a", tokenA)
                val b = FakeDevice("b", tokenB)
                a.connect(http, url)
                b.connect(http, url)
                val idA = hello(a)
                val idB = hello(b)

                val traces = mutableListOf<String>()
                repeat(rounds) { round ->
                    val aSends = round % 2 == 0
                    val sender = if (aSends) a else b
                    val receiver = if (aSends) b else a
                    val senderId = if (aSends) idA else idB
                    val receiverId = if (aSends) idB else idA

                    sender.send(create(receiverId, UUID.randomUUID().toString()))
                    val accepted = sender.expect("task.ack", FRAME_TIMEOUT_MS)
                    val taskId = accepted.bodyString("task")
                        ?: error("round ${round + 1}: the ack carried no task")

                    val delivered = receiver.expect("task.create", FRAME_TIMEOUT_MS)
                    check(delivered.task == taskId) {
                        "round ${round + 1}: delivered ${delivered.task}, expected $taskId"
                    }

                    // Ack the delivery, as the real client does: the task leaves
                    // `delivered`, the sender sees the relayed ack, and a second
                    // run does not receive this task again.
                    receiver.send(
                        Envelope(
                            v = 1,
                            id = UUID.randomUUID().toString(),
                            type = "task.ack",
                            ts = System.currentTimeMillis(),
                            trace = accepted.trace,
                            to = senderId,
                            task = taskId,
                            body = buildJsonObject { put("state", "delivered") },
                        ),
                    )
                    val relayed = sender.expect("task.ack", FRAME_TIMEOUT_MS)
                    check(relayed.task == taskId) {
                        "round ${round + 1}: relayed ack carried ${relayed.task}, expected $taskId"
                    }
                    receiver.expect("task.ack", FRAME_TIMEOUT_MS)

                    traces += accepted.trace ?: error("round ${round + 1}: the ack carried no trace")
                }
                traces
            } finally {
                http.close()
            }
        }

    private suspend fun hello(device: FakeDevice): String {
        device.send(
            Envelope(
                v = 1,
                id = UUID.randomUUID().toString(),
                type = "device.hello",
                ts = System.currentTimeMillis(),
                body = buildJsonObject {
                    put("platform", "android")
                    put("name", "exit-run")
                    put("appVersion", "0.1.0")
                },
            ),
        )
        return device.expect("device.hello.ack", FRAME_TIMEOUT_MS).to
            ?: error("the hello ack carried no device id")
    }

    private fun create(to: String, messageId: String) = Envelope(
        v = 1,
        id = messageId,
        type = "task.create",
        ts = System.currentTimeMillis(),
        to = to,
        body = buildJsonObject { put("goal", "exit run round-trip") },
    )
}

fun main(args: Array<String>) {
    val values = args.toList().chunked(2)
        .filter { it.size == 2 }
        .associate { it[0].removePrefix("--") to it[1] }
    val url = values["url"]
        ?: error("usage: exitRun --url wss://... --token-a <a> --token-b <b> [--rounds 20]")
    val tokenA = values["token-a"] ?: error("--token-a is required")
    val tokenB = values["token-b"] ?: error("--token-b is required")
    val rounds = values["rounds"]?.toIntOrNull() ?: 20

    val traces = ExitRun.run(url, tokenA, tokenB, rounds)
    traces.forEachIndexed { index, trace ->
        println("ok ${index + 1}/${traces.size} trace=$trace")
    }
}
