package com.hopa.backend.gateway

import com.hopa.backend.ServerConfig
import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.board.BoardAck
import com.hopa.backend.board.BoardGateway
import com.hopa.backend.board.BoardResult
import com.hopa.backend.board.BoardService
import com.hopa.backend.protocol.DecodeResult
import com.hopa.backend.protocol.Envelope
import com.hopa.backend.protocol.EnvelopeValidator
import com.hopa.backend.protocol.GatewayCodec
import com.hopa.backend.tasks.RouteResult
import com.hopa.backend.tasks.TaskRecord
import com.hopa.backend.tasks.TaskRouter
import com.hopa.backend.trace.TraceIds
import io.ktor.http.HttpHeaders
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.request.header
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.pingPeriod
import io.ktor.server.websocket.timeout
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** How long a revoked socket gets to complete the close handshake. */
internal const val KICK_CLOSE_GRACE_MS = 1_000L

/**
 * Waits for a kick, offers the peer a bounded time to answer the Close frame,
 * then cancels the incoming channel unconditionally. Ktor's `close()` only
 * sends and flushes the frame; the read loop ends when `incoming` ends, which a
 * peer that never answers would otherwise postpone indefinitely.
 */
internal suspend fun closeKickedSession(
    closed: Deferred<Unit>,
    graceMs: Long = KICK_CLOSE_GRACE_MS,
    sendClose: suspend () -> Unit,
    cancelIncoming: () -> Unit,
) {
    closed.await()
    try {
        withTimeoutOrNull(graceMs) { sendClose() }
    } finally {
        // Even a close() that returned (or threw) leaves incoming open until
        // the peer answers; cancel it so the read loop cannot outlive the kick.
        cancelIncoming()
    }
}

fun Application.gatewayRoutes(
    config: ServerConfig,
    registry: DeviceRegistry = config.registry,
    router: TaskRouter = TaskRouter(
        store = config.tasks,
        registry = registry,
        devices = config.devices,
        log = config.log,
        push = config.push,
        traces = config.traces,
    ),
    board: BoardGateway = BoardGateway(
        service = BoardService(config.boardStore),
        registry = registry,
        devices = config.devices,
        push = config.push,
        traces = config.traces,
        log = config.log,
    ),
) {
    install(WebSockets) {
        pingPeriod = 25.seconds
        timeout = 90.seconds
        // Four times the contract cap: a frame the client should not have sent
        // still reaches the validator, which answers E_SCHEMA. Ktor only closes
        // the socket itself beyond this, so the limit is a fail-safe, not the
        // contract.
        maxFrameSize = EnvelopeValidator.MAX_FRAME_BYTES * 4L
    }

    routing {
        webSocket("/v1/gateway") {
            val token = call.request.header(HttpHeaders.Authorization)
                ?.removePrefix("Bearer ")
                ?.trim()
            val identity = token?.takeIf { it.isNotEmpty() }?.let(config.auth::deviceFor)
            if (identity == null) {
                send(Frame.Text(GatewayCodec.encode(errorFrame("E_AUTH", "unknown or expired token"))))
                close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "E_AUTH"))
                return@webSocket
            }

            val outgoing = Channel<String>(Channel.BUFFERED)
            val writer = launch {
                for (text in outgoing) {

                    send(Frame.Text(text))
                }
            }
            val session = DeviceRegistry.Session(identity, outgoing)
            val attached = registry.attach(session)
            // A revoke completes the session's close signal from another
            // coroutine. Close with a bounded grace: a peer that stalls the
            // close handshake must not hold the read loop to the ping timeout.
            val kicked = launch {
                closeKickedSession(
                    closed = session.closed,
                    sendClose = {
                        close(CloseReason(CloseReason.Codes.GOING_AWAY, "revoked"))
                    },
                    cancelIncoming = { incoming.cancel(CancellationException("revoked")) },
                )
            }

            try {
                // Revoked between deviceFor and attach: the tombstone already
                // completed the close signal, so leave without attaching.
                if (!attached) return@webSocket
                for (frame in incoming) {
                    if (frame !is Frame.Text) continue
                    val raw = frame.readText()
                    val decoded = GatewayCodec.decode(raw)
                    val failure = decoded as? DecodeResult.Failed
                    if (failure != null) {
                        outgoing.send(
                            GatewayCodec.encode(
                                errorFrame(failure.error.code, failure.error.message),
                            ),
                        )
                        continue
                    }

                    val envelope = (decoded as DecodeResult.Ok).envelope
                    val problem = EnvelopeValidator.validate(raw, envelope)
                    if (problem != null) {
                        outgoing.send(GatewayCodec.encode(errorFrame(problem.code, problem.message)))
                        continue
                    }

                    val traceparent = EnvelopeValidator.traceOf(envelope)
                    config.traces.span(
                        identity.householdId,
                        TraceIds.traceId(traceparent)
                            ?: TraceIds.traceId(TraceIds.newTraceparent())!!,
                        "accept",
                        identity.deviceId,
                        envelope.task,
                    ) {}

                    when (envelope.type) {
                        "device.hello" -> {
                            // A revoke landed after attach: never join presence.
                            if (!registry.join(session)) return@webSocket
                            config.devices.touch(
                                identity.deviceId,
                                identity.householdId,
                                envelope.body["pushToken"]?.jsonPrimitive?.contentOrNull,
                                System.currentTimeMillis(),
                            )
                            val ids = registry.deviceIds(identity.householdId)
                            val peers = registry.peers(identity.householdId, identity.deviceId)
                            val queued = router.queuedFor(identity.deviceId, identity.householdId)
                            // The newcomer learns the roster from the ack; one
                            // event per join tells the others, so a replay or an
                            // interleaved handshake cannot announce it twice.
                            outgoing.send(
                                GatewayCodec.encode(
                                    helloAck(identity.deviceId, queued = queued, peers = ids),
                                ),
                            )
                            peers.forEach { peer ->
                                peer.outgoing.trySend(
                                    GatewayCodec.encode(
                                        presence(identity.deviceId, online = true, ids),
                                    ),
                                )
                            }
                            router.deliverPending(identity.deviceId, identity.householdId)
                        }

                        "ping" -> outgoing.send(
                            GatewayCodec.encode(serverFrame("pong", identity.deviceId)),
                        )

                        // One frame in, one answer back, in the order sent: the
                        // ack (or error) of the n-th op is the n-th answer.
                        "board.op" -> {
                            if (!config.messageLimit.allow(identity.deviceId)) {
                                outgoing.send(
                                    GatewayCodec.encode(errorFrame("E_RATE", "too many messages", trace = traceparent)),
                                )
                                continue
                            }
                            when (val result = board.append(identity, envelope, traceparent)) {
                                is BoardResult.Ok -> outgoing.send(
                                    GatewayCodec.encode(boardAckFrame(identity.deviceId, result.value, traceparent)),
                                )

                                is BoardResult.Failed -> outgoing.send(
                                    GatewayCodec.encode(
                                        errorFrame(result.error.code, result.error.message, trace = traceparent),
                                    ),
                                )
                            }
                        }

                        else -> {
                            if (envelope.type.startsWith("task.") &&
                                !config.messageLimit.allow(identity.deviceId)
                            ) {
                                outgoing.send(
                                    GatewayCodec.encode(errorFrame("E_RATE", "too many messages")),
                                )
                                continue
                            }
                            when (val result = router.route(identity, envelope, traceparent)) {
                                is RouteResult.Accepted -> outgoing.send(
                                    GatewayCodec.encode(
                                        acceptedFrame(identity.deviceId, result.task, result.duplicate),
                                    ),
                                )

                                is RouteResult.Refused -> outgoing.send(
                                    GatewayCodec.encode(
                                        errorFrame(
                                            result.error.code,
                                            result.error.message,
                                            trace = result.trace,
                                        ),
                                    ),
                                )
                            }
                        }
                    }
                }
            } finally {
                kicked.cancel()
                registry.detach(session)
                registry.leave(session)
                // A reconnect replaces the session; the old socket closing then
                // must not tell peers that a live device went away.
                if (registry.sessionFor(identity.deviceId) == null) {
                    val remaining = registry.deviceIds(identity.householdId)
                    registry.peers(identity.householdId, identity.deviceId).forEach { peer ->
                        peer.outgoing.trySend(
                            GatewayCodec.encode(
                                presence(identity.deviceId, online = false, remaining),
                            ),
                        )
                    }
                }
                outgoing.close()
                writer.cancel()
            }
        }
    }
}

/** Server-authored frame. The id is ours; [to] is the device that receives it. */
private fun serverFrame(
    type: String,
    to: String?,
    body: JsonObject = JsonObject(emptyMap()),
    trace: String? = null,
): Envelope = Envelope(
    v = 1,
    id = UUID.randomUUID().toString(),
    type = type,
    ts = System.currentTimeMillis(),
    trace = trace,
    to = to,
    body = body,
)

private fun errorFrame(code: String, message: String, trace: String? = null): Envelope =
    serverFrame(
        type = "error",
        to = null,
        trace = trace,
        body = buildJsonObject {
            put("code", code)
            put("message", message)
            put("retryable", code == "E_QUEUE" || code == "E_RATE")
        },
    )

private fun acceptedFrame(deviceId: String, task: TaskRecord, duplicate: Boolean): Envelope =
    serverFrame(
        type = "task.ack",
        to = deviceId,
        trace = task.trace,
        body = buildJsonObject {
            put("task", task.id)
            put("state", task.state)
            put("duplicate", duplicate)
        },
    )

private fun boardAckFrame(deviceId: String, ack: BoardAck, traceparent: String): Envelope =
    serverFrame(
        type = "board.op.ack",
        to = deviceId,
        trace = traceparent,
        body = buildJsonObject {
            put("scope", ack.scope)
            put("seq", ack.seq)
            put("duplicate", ack.duplicate)
        },
    )

private fun helloAck(deviceId: String, queued: Int, peers: List<String>): Envelope =
    serverFrame(
        type = "device.hello.ack",
        to = deviceId,
        body = buildJsonObject {
            put("session", UUID.randomUUID().toString())
            put("serverTime", System.currentTimeMillis())
            put("heartbeatSec", 25)
            put("queued", queued)
            put("peers", JsonArray(peers.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        },
    )

private fun presence(deviceId: String, online: Boolean, peers: List<String>): Envelope =
    serverFrame(
        type = "presence",
        to = null,
        body = buildJsonObject {
            put("device", deviceId)
            put("online", online)
            put("peers", JsonArray(peers.map { kotlinx.serialization.json.JsonPrimitive(it) }))
        },
    )
