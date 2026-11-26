package com.hopa.backend.board

import com.hopa.backend.ServerConfig
import com.hopa.backend.http.BodyTooLarge
import com.hopa.backend.http.bearerDevice
import com.hopa.backend.http.receiveBounded
import com.hopa.backend.http.respondError
import com.hopa.backend.http.statusFor
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import java.time.Instant
import java.util.Base64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable

/** One op as a device pulls it: the server-attested actor and the ciphertext it never read. */
@Serializable
internal class BoardOpView(
    val seq: Long,
    val opId: String,
    val fromDevice: String,
    val memberId: String,
    val at: String,
    val keyEpoch: Int,
    val ct: String,
)

@Serializable
internal class BoardOpsResponse(val scope: String, val ops: List<BoardOpView>, val hasMore: Boolean)

@Serializable
internal class BoardHeadResponse(val heads: Map<String, Long>)

@Serializable
internal class BoardCursorRequest(val scope: String, val seq: Long)

/**
 * The board's pull side (ADR-0033): ops after a seq, the head of every scope
 * the device may read, and the device's cursor. Every route answers `E_SCOPE`
 * (403) for `parents` unless the device holds the current parents key.
 */
fun Application.boardRoutes(config: ServerConfig, service: BoardService = BoardService(config.boardStore)) {
    routing {
        get("/v1/board/ops") {
            val identity = call.bearerDevice(config) ?: return@get
            val parameters = call.request.queryParameters
            val scope = parameters["scope"]
            val after = parameters["after"]?.let { it.toLongOrNull() ?: -1L } ?: 0L
            val limit = parameters["limit"]?.let { it.toIntOrNull() ?: 0 }
            if (scope == null) {
                call.respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "scope is required")
                return@get
            }
            when (val result = service.opsAfter(identity, scope, after, limit)) {
                is BoardResult.Ok -> call.respond(
                    BoardOpsResponse(scope, result.value.ops.map(::view), result.value.hasMore),
                )

                is BoardResult.Failed -> call.respondFailure(result)
            }
        }

        get("/v1/board/head") {
            val identity = call.bearerDevice(config) ?: return@get
            call.respond(BoardHeadResponse(service.head(identity)))
        }

        put("/v1/board/cursor") {
            val identity = call.bearerDevice(config) ?: return@put
            val request = try {
                call.receiveBounded(BoardCursorRequest.serializer())
            } catch (_: BodyTooLarge) {
                call.respondError(HttpStatusCode.PayloadTooLarge, "E_SCHEMA", "body is too large")
                return@put
            } catch (_: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "body needs scope and seq")
                return@put
            } catch (_: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "body needs scope and seq")
                return@put
            }
            when (val result = service.putCursor(identity, request.scope, request.seq)) {
                is BoardResult.Ok -> call.respond(HttpStatusCode.NoContent)
                is BoardResult.Failed -> call.respondFailure(result)
            }
        }
    }
}

private fun view(op: BoardOp) = BoardOpView(
    seq = op.seq,
    opId = op.opId,
    fromDevice = op.fromDevice,
    memberId = op.memberId,
    at = Instant.ofEpochMilli(op.createdAt).toString(),
    keyEpoch = op.keyEpoch,
    ct = Base64.getEncoder().encodeToString(op.body),
)

internal suspend fun ApplicationCall.respondFailure(result: BoardResult.Failed) {
    respondError(statusFor(result.error.code), result.error.code, result.error.message)
}
