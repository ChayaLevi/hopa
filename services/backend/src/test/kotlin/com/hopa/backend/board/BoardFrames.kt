package com.hopa.backend.board

import com.hopa.backend.harness.FakeDevice
import com.hopa.backend.protocol.Envelope
import io.ktor.client.HttpClient
import java.util.Base64
import java.util.UUID
import kotlin.random.Random
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Board frames and connections for gateway tests. */
object BoardFrames {
    fun hello(deviceId: String) = Envelope(
        v = 1,
        id = UUID.randomUUID().toString(),
        type = "device.hello",
        ts = 0,
        from = deviceId,
        body = buildJsonObject {
            put("platform", "android")
            put("name", "Pixel 9")
            put("appVersion", "0.1.0")
        },
    )

    /** Random bytes standing in for a ciphertext: the server never reads it. */
    fun ciphertext(bytes: Int = 64): ByteArray = Random.nextBytes(bytes)

    fun op(
        scope: String,
        keyEpoch: Int = if (scope == BoardScopes.FAMILY) 0 else 1,
        ct: ByteArray = ciphertext(),
        opId: String = UUID.randomUUID().toString(),
        trace: String? = null,
    ) = Envelope(
        v = 1,
        id = opId,
        type = "board.op",
        ts = 1,
        trace = trace,
        body = buildJsonObject {
            put("scope", scope)
            put("ct", Base64.getEncoder().encodeToString(ct))
            put("keyEpoch", keyEpoch)
        },
    )

    /** Connects with token `tok-<deviceId>` and completes the hello. */
    suspend fun online(http: HttpClient, deviceId: String): FakeDevice {
        val device = FakeDevice(deviceId, "tok-$deviceId")
        device.connect(http)
        device.send(hello(deviceId))
        device.expect("device.hello.ack")
        return device
    }
}
