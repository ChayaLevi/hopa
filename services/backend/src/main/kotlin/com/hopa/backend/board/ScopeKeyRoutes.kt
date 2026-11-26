package com.hopa.backend.board

import com.hopa.backend.ServerConfig
import com.hopa.backend.http.BodyTooLarge
import com.hopa.backend.http.bearerDevice
import com.hopa.backend.http.newTraceId
import com.hopa.backend.http.receiveBounded
import com.hopa.backend.http.respondError
import com.hopa.backend.http.statusFor
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable

/** This device's wrap of the scope key at the current epoch, and who wrapped it. */
@Serializable
internal class ScopeKeyView(
    val scope: String,
    val keyEpoch: Int,
    val wrappedKey: String,
    val wrapperDeviceId: String? = null,
    val wrapperPublicKey: String? = null,
)

/**
 * The 404 answer: the standard error fields, plus where the key stands. With
 * no key in the household, [bootstrapEpoch] is the epoch a parent device
 * creates next, and [lostEpoch] (when set) is the earlier epoch whose ops no
 * device can read any more: the client must say so.
 */
@Serializable
internal class ScopeKeyMissing(
    val code: String,
    val message: String,
    val retryable: Boolean = false,
    val traceId: String,
    val currentEpoch: Int? = null,
    val bootstrapEpoch: Int? = null,
    val lostEpoch: Int? = null,
)

@Serializable
internal class ScopeKeyWrapRequest(val deviceId: String, val wrappedKey: String, val keyEpoch: Int)

@Serializable
internal class ScopeKeyStored(
    val scope: String,
    val deviceId: String,
    val keyEpoch: Int,
    val bootstrap: Boolean,
    val lostEpoch: Int? = null,
)

/**
 * Scope keys (ADR-0033): a device reads its own wrap of the parents key, and a
 * parent's device hands the key to another parent's device, or bootstraps it.
 * The server stores 60-byte wraps it cannot open; which public key a wrap is
 * for was verified on the devices (D16), never here.
 */
fun Application.scopeKeyRoutes(config: ServerConfig, store: BoardStore = config.boardStore) {
    routing {
        get("/v1/scope-keys/{scope}") {
            val identity = call.bearerDevice(config) ?: return@get
            val scope = call.wrappedScope() ?: return@get
            val wrap = store.scopeKeyWrap(identity.householdId, identity.deviceId, scope)
            if (wrap != null) {
                call.respond(
                    ScopeKeyView(
                        scope = scope,
                        keyEpoch = wrap.keyEpoch,
                        wrappedKey = encode(wrap.wrappedKey),
                        wrapperDeviceId = wrap.wrapperDeviceId,
                        wrapperPublicKey = wrap.wrapperPublicKey?.let(::encode),
                    ),
                )
                return@get
            }
            val state = store.scopeKeyState(identity.householdId, scope)
            call.respond(
                HttpStatusCode.NotFound,
                ScopeKeyMissing(
                    code = "E_SCOPE",
                    message = if (state.currentEpoch == null) {
                        "no device holds a key for scope $scope"
                    } else {
                        "this device holds no current key for scope $scope"
                    },
                    traceId = newTraceId(),
                    currentEpoch = state.currentEpoch,
                    bootstrapEpoch = state.bootstrapEpoch,
                    lostEpoch = state.lostEpoch,
                ),
            )
        }

        post("/v1/scope-keys/{scope}") {
            val identity = call.bearerDevice(config) ?: return@post
            val scope = call.wrappedScope() ?: return@post
            val body = try {
                call.receiveBounded(ScopeKeyWrapRequest.serializer())
            } catch (_: BodyTooLarge) {
                call.respondError(HttpStatusCode.PayloadTooLarge, "E_SCHEMA", "body is too large")
                return@post
            } catch (_: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "body needs deviceId, wrappedKey and keyEpoch")
                return@post
            } catch (_: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "body needs deviceId, wrappedKey and keyEpoch")
                return@post
            }
            val deviceId = canonicalUuid(body.deviceId)
            val wrapped = decode(body.wrappedKey)
            when {
                deviceId == null ->
                    call.respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "deviceId must be a uuid")

                wrapped == null || wrapped.size != WRAP_BYTES ->
                    call.respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "wrappedKey must be $WRAP_BYTES bytes of base64")

                body.keyEpoch < 1 ->
                    call.respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "keyEpoch must be positive")

                else -> {
                    val request = WrapRequest(scope, identity.deviceId, deviceId, body.keyEpoch, wrapped)
                    when (val outcome = store.writeWrap(identity.householdId, request)) {
                        is WrapOutcome.Stored -> call.respond(
                            HttpStatusCode.Created,
                            ScopeKeyStored(scope, deviceId, outcome.keyEpoch, outcome.bootstrap, outcome.lostEpoch),
                        )

                        is WrapOutcome.Refused -> call.respondError(
                            statusFor(outcome.error.code),
                            outcome.error.code,
                            outcome.error.message,
                        )
                    }
                }
            }
        }
    }
}

/** The KeyWrap device blob: IV(12) || key(32) || tag(16). */
private const val WRAP_BYTES = 60

/** Only `parents` has a scope key; `family` reads with the household key. */
private suspend fun ApplicationCall.wrappedScope(): String? {
    val scope = parameters["scope"]
    if (scope != BoardScopes.PARENTS) {
        respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "only the parents scope has a scope key")
        return null
    }
    return scope
}

private fun canonicalUuid(value: String): String? =
    runCatching { UUID.fromString(value).toString() }
        .getOrNull()
        ?.takeIf { it == value.lowercase() }

private fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

/** base64 or base64url, padded or not, as the pairing routes accept wraps. */
private fun decode(value: String): ByteArray? =
    try {
        Base64.getDecoder().decode(value)
    } catch (_: IllegalArgumentException) {
        try {
            Base64.getUrlDecoder().decode(value)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
