package com.hopa.backend.protocol

import java.security.SecureRandom
import java.util.Base64
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

/**
 * Protocol v0 rules that are not expressible in JSON Schema, or that must hold
 * even when the schema is bypassed by a hand-built frame.
 *
 * Order matters: the cheapest refusal wins, so an oversized frame never reaches
 * the decoder.
 */
object EnvelopeValidator {
    const val MAX_FRAME_BYTES = 65_536

    val TYPES: Set<String> = setOf(
        "device.hello",
        "device.hello.ack",
        "presence",
        "task.create",
        "task.needs_approval",
        "task.ack",
        "task.progress",
        "task.result",
        "task.error",
        "task.cancel",
        // Server-authored diagnostics. These are the only plaintext payloads:
        // the admin route refuses any other type and the body is not encrypted.
        "diag.ping",
        "diag.state",
        "diag.reset",
        // The family board (ADR-0033): a device appends an encrypted op; the
        // server acks it and tells the other devices that may read the scope.
        "board.op",
        "board.op.ack",
        "board.changed",
        "error",
        "ping",
        "pong",
    )

    private val TRACEPARENT = Regex("^00-[0-9a-f]{32}-[0-9a-f]{16}-01$")
    private val MESSAGE_ID =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
    private val random = SecureRandom()

    fun validate(raw: String, envelope: Envelope): GatewayError? = when {
        raw.toByteArray(Charsets.UTF_8).size > MAX_FRAME_BYTES ->
            GatewayError("E_SCHEMA", "frame is larger than $MAX_FRAME_BYTES bytes")

        envelope.v != 1 ->
            GatewayError("E_VERSION", "unsupported protocol version ${envelope.v}")

        envelope.type !in TYPES ->
            GatewayError("E_UNSUPPORTED", "unsupported message type")

        envelope.ts < 0 ->
            GatewayError("E_SCHEMA", "negative timestamp")

        !MESSAGE_ID.matches(envelope.id) ->
            GatewayError("E_SCHEMA", "message id is not a UUID")

        envelope.from?.isBlank() == true ||
            envelope.to?.isBlank() == true ||
            envelope.task?.isBlank() == true ->
            GatewayError("E_SCHEMA", "blank device or task id")

        (envelope.type.startsWith("task.") || envelope.type.startsWith("diag.")) &&
            envelope.to == null ->
            GatewayError("E_SCHEMA", "a task message needs a target device")

        envelope.type == "board.op" -> boardOpProblem(envelope)

        else -> null
    }

    /** IV (12) and GCM tag (16): the shortest well-formed op ciphertext. */
    const val MIN_BOARD_CIPHERTEXT_BYTES = 28

    /** 44 KiB raw, so a `board.op` frame stays under [MAX_FRAME_BYTES] after base64. */
    const val MAX_BOARD_CIPHERTEXT_BYTES = 44 * 1024

    val BOARD_SCOPES: Set<String> = setOf("family", "parents")

    /**
     * The plaintext shape of a `board.op` body: a known scope, a base64 `ct`
     * within the size bounds and a non-negative `keyEpoch`. The ciphertext
     * itself is never read; which epoch a scope needs is the board service's
     * rule, not the frame's.
     */
    private fun boardOpProblem(envelope: Envelope): GatewayError? {
        val body = envelope.body
        val scope = (body["scope"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
        if (scope !in BOARD_SCOPES) return GatewayError("E_SCHEMA", "board.op needs a known scope")
        val epoch = (body["keyEpoch"] as? JsonPrimitive)?.takeIf { !it.isString }?.intOrNull
        if (epoch == null || epoch < 0) return GatewayError("E_SCHEMA", "board.op needs a non-negative keyEpoch")
        val ct = (body["ct"] as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull
            ?: return GatewayError("E_SCHEMA", "board.op needs ct")
        val raw = decodeCiphertext(ct) ?: return GatewayError("E_SCHEMA", "ct is not base64")
        return when {
            raw.size < MIN_BOARD_CIPHERTEXT_BYTES -> GatewayError("E_SCHEMA", "ct is shorter than IV and tag")
            raw.size > MAX_BOARD_CIPHERTEXT_BYTES ->
                GatewayError("E_SCHEMA", "ct is larger than $MAX_BOARD_CIPHERTEXT_BYTES bytes")
            else -> null
        }
    }

    /** Standard base64 with padding, as the schema's `ct` pattern says; null when it is not. */
    fun decodeCiphertext(ct: String): ByteArray? =
        try {
            Base64.getDecoder().decode(ct)
        } catch (_: IllegalArgumentException) {
            null
        }

    /**
     * A valid traceparent is kept so a client's chain survives the hop; anything
     * else is replaced rather than refused, because a bad trace must not cost a
     * task.
     */
    fun traceOf(envelope: Envelope): String {
        val trace = envelope.trace
        if (trace != null && TRACEPARENT.matches(trace)) {
            return trace
        }
        return "00-${hex(16)}-${hex(8)}-01"
    }

    private fun hex(bytes: Int): String {
        val buffer = ByteArray(bytes)
        random.nextBytes(buffer)
        return buffer.joinToString("") { "%02x".format(it) }
    }
}
