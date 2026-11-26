package com.hopa.backend.board.client

import com.hopa.backend.wire.BackendEnvelope
import com.hopa.contracts.json.HopaJson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import java.util.Base64

/** One op as the server stores it: what it attests, and the ciphertext it never read. */
data class RemoteOp(
    val seq: Long,
    val opId: String,
    val fromDevice: String,
    val memberId: String,
    val at: String,
    val keyEpoch: Int,
    val ct: ByteArray,
) {
    override fun equals(other: Any?): Boolean = other is RemoteOp && other.seq == seq && other.opId == opId &&
        other.ct.contentEquals(ct)

    override fun hashCode(): Int = 31 * seq.hashCode() + opId.hashCode()
}

data class BoardPage(
    val scope: String,
    val ops: List<RemoteOp>,
    val hasMore: Boolean,
)

/** What the server said to an appended op. */
sealed interface AppendResult {
    data class Acked(val scope: String, val seq: Long, val duplicate: Boolean) : AppendResult

    /** `E_SCOPE`, `E_CONFLICT`, `E_SCHEMA`, `E_RATE`, ...: screens read [code], never the message. */
    data class Refused(val code: String, val retryable: Boolean) : AppendResult
}

/** The answer to "my wrap of the scope key". */
sealed interface ScopeKeyAnswer {
    data class Held(
        val keyEpoch: Int,
        val wrappedKey: ByteArray,
        val wrapperDeviceId: String?,
        val wrapperPublicKey: ByteArray?,
    ) :
        ScopeKeyAnswer {
        override fun equals(other: Any?): Boolean = other is Held && other.keyEpoch == keyEpoch &&
            other.wrappedKey.contentEquals(wrappedKey)

        override fun hashCode(): Int = keyEpoch
    }

    /**
     * No wrap here. With no key in the household, [bootstrapEpoch] is the epoch to create; [lostEpoch],
     * when set, is an earlier epoch whose `parents` ops no device can read any more (say so).
     */
    data class Missing(val currentEpoch: Int?, val bootstrapEpoch: Int?, val lostEpoch: Int?) : ScopeKeyAnswer

    data class Refused(val status: Int, val code: String?) : ScopeKeyAnswer
}

/** A failure the board link could not turn into an answer. */
class BoardLinkException(val status: Int, val code: String?) : RuntimeException("board call failed: $status $code")

/**
 * The client side of the family board's wire (PLAN §4): `board.op` over the gateway socket, the pull,
 * head and cursor routes, and the scope-key routes. It moves ciphertext only; [BoardCipher] seals and
 * opens. The app wires [receive] to the gateway's board frames (`BackendLink.onBoard`) and [send] to
 * `BackendLink.send`.
 *
 * Appends are one at a time: the gateway answers in send order, so the next `board.op.ack` or
 * `error` on the socket is the answer to the op in flight.
 */
class BoardLink(
    private val http: BoardHttp,
    private val send: suspend (BackendEnvelope) -> Unit,
    private val clock: () -> Long = System::currentTimeMillis,
    private val ackTimeoutMs: Long = DEFAULT_ACK_TIMEOUT_MS,
) {
    private val appending = Mutex()

    @Volatile
    private var inFlight: CompletableDeferred<AppendResult>? = null

    /** Called with `board.changed {scope, seq}`: someone else appended; pull [scope] after your cursor. */
    @Volatile
    var onChanged: (suspend (scope: String, seq: Long) -> Unit)? = null

    fun opFrame(scope: String, opId: String, keyEpoch: Int, ct: ByteArray): BackendEnvelope = BackendEnvelope(
        id = opId,
        type = "board.op",
        ts = clock(),
        body = buildJsonObject {
            put("scope", scope)
            put("ct", Base64.getEncoder().encodeToString(ct))
            put("keyEpoch", keyEpoch)
        },
    )

    /** Appends one sealed op and waits for the server's answer. The envelope id is the op id. */
    suspend fun append(scope: String, opId: String, keyEpoch: Int, ct: ByteArray): AppendResult = appending.withLock {
        val answer = CompletableDeferred<AppendResult>()
        inFlight = answer
        try {
            send(opFrame(scope, opId, keyEpoch, ct))
            withTimeout(ackTimeoutMs) { answer.await() }
        } finally {
            inFlight = null
        }
    }

    /** Board frames from the gateway: acks and errors answer the op in flight; changes are passed on. */
    suspend fun receive(frame: BackendEnvelope) {
        when (frame.type) {
            "board.op.ack" -> inFlight?.complete(
                AppendResult.Acked(
                    scope = frame.body.getValue("scope").jsonPrimitive.content,
                    seq = frame.body.getValue("seq").jsonPrimitive.long,
                    duplicate = frame.body["duplicate"]?.jsonPrimitive?.boolean ?: false,
                ),
            )
            "error" -> inFlight?.complete(
                AppendResult.Refused(
                    code = frame.body["code"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    retryable = frame.body["retryable"]?.jsonPrimitive?.boolean ?: false,
                ),
            )
            "board.changed" -> onChanged?.invoke(
                frame.body.getValue("scope").jsonPrimitive.content,
                frame.body.getValue("seq").jsonPrimitive.long,
            )
        }
    }

    /** One page of [scope] after [after]. */
    suspend fun pull(scope: String, after: Long, limit: Int = MAX_PAGE): BoardPage {
        val answer = http.get("/v1/board/ops?scope=$scope&after=$after&limit=$limit")
        val body = ok(answer)
        return BoardPage(
            scope = body.getValue("scope").jsonPrimitive.content,
            hasMore = body.getValue("hasMore").jsonPrimitive.boolean,
            ops = body.getValue("ops").jsonArray.map { element ->
                val op = element.jsonObject
                RemoteOp(
                    seq = op.getValue("seq").jsonPrimitive.long,
                    opId = op.getValue("opId").jsonPrimitive.content,
                    fromDevice = op.getValue("fromDevice").jsonPrimitive.content,
                    memberId = op.getValue("memberId").jsonPrimitive.content,
                    at = op.getValue("at").jsonPrimitive.content,
                    keyEpoch = op.getValue("keyEpoch").jsonPrimitive.int,
                    ct = Base64.getDecoder().decode(op.getValue("ct").jsonPrimitive.content),
                )
            },
        )
    }

    /** Every op of [scope] after [after], page by page. */
    suspend fun pullAll(scope: String, after: Long): List<RemoteOp> {
        val out = mutableListOf<RemoteOp>()
        var cursor = after
        do {
            val page = pull(scope, cursor)
            out += page.ops
            cursor = page.ops.lastOrNull()?.seq ?: cursor
        } while (page.hasMore && page.ops.isNotEmpty())
        return out
    }

    /** The latest seq of each scope this device may read. */
    suspend fun head(): Map<String, Long> =
        ok(http.get("/v1/board/head")).getValue("heads").jsonObject.mapValues { it.value.jsonPrimitive.long }

    /** Records what this device has applied in [scope]. */
    suspend fun putCursor(scope: String, seq: Long) {
        val answer = http.put(
            "/v1/board/cursor",
            buildJsonObject {
                put("scope", scope)
                put("seq", seq)
            }.toString(),
        )
        if (answer.status != NO_CONTENT && answer.status != OK) throw failure(answer)
    }

    suspend fun scopeKey(scope: String): ScopeKeyAnswer {
        val answer = http.get("/v1/scope-keys/$scope")
        val body = parse(answer)
        return when (answer.status) {
            OK -> ScopeKeyAnswer.Held(
                keyEpoch = body.getValue("keyEpoch").jsonPrimitive.int,
                wrappedKey = b64url(body.getValue("wrappedKey").jsonPrimitive.content),
                wrapperDeviceId = body["wrapperDeviceId"]?.jsonPrimitive?.contentOrNull,
                wrapperPublicKey = body["wrapperPublicKey"]?.jsonPrimitive?.contentOrNull?.let(::b64url),
            )
            NOT_FOUND -> ScopeKeyAnswer.Missing(
                currentEpoch = body["currentEpoch"]?.jsonPrimitive?.intOrNull,
                bootstrapEpoch = body["bootstrapEpoch"]?.jsonPrimitive?.intOrNull,
                lostEpoch = body["lostEpoch"]?.jsonPrimitive?.intOrNull,
            )
            else -> ScopeKeyAnswer.Refused(answer.status, body["code"]?.jsonPrimitive?.contentOrNull)
        }
    }

    /** Stores [wrappedKey] for [deviceId] at [keyEpoch] (a bootstrap when it is this device's own). */
    suspend fun putScopeKey(scope: String, deviceId: String, wrappedKey: ByteArray, keyEpoch: Int): HttpAnswer =
        http.post(
            "/v1/scope-keys/$scope",
            buildJsonObject {
                put("deviceId", deviceId)
                put("wrappedKey", Base64.getUrlEncoder().withoutPadding().encodeToString(wrappedKey))
                put("keyEpoch", keyEpoch)
            }.toString(),
        )

    private fun b64url(text: String): ByteArray = Base64.getUrlDecoder().decode(text)

    private fun parse(answer: HttpAnswer): JsonObject = if (answer.body.isBlank()) {
        JsonObject(
            emptyMap(),
        )
    } else {
        HopaJson.element.parseToJsonElement(answer.body).jsonObject
    }

    private fun ok(answer: HttpAnswer): JsonObject = if (answer.status == OK) parse(answer) else throw failure(answer)

    private fun failure(answer: HttpAnswer): BoardLinkException {
        val code = runCatching { parse(answer)["code"]?.jsonPrimitive?.contentOrNull }.getOrNull()
        return BoardLinkException(answer.status, code)
    }

    companion object {
        const val MAX_PAGE = 500
        const val DEFAULT_ACK_TIMEOUT_MS = 15_000L
        private const val OK = 200
        private const val NO_CONTENT = 204
        private const val NOT_FOUND = 404
    }
}
