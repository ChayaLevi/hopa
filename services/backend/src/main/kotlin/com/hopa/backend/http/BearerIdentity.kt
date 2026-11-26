package com.hopa.backend.http

import com.hopa.backend.ServerConfig
import com.hopa.backend.auth.DeviceIdentity
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.header

/**
 * The device behind the bearer token, or null after answering 401 `E_AUTH`.
 * The household, member and device always come from here, never from the request.
 */
suspend fun ApplicationCall.bearerDevice(config: ServerConfig): DeviceIdentity? {
    val token = request.header(HttpHeaders.Authorization)
        ?.removePrefix("Bearer ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val identity = token?.let(config.auth::deviceFor)
    if (identity == null) {
        respondError(HttpStatusCode.Unauthorized, "E_AUTH", "unknown or expired token")
    }
    return identity
}

/** The HTTP status for a stable error code the board and scope-key routes answer with. */
fun statusFor(code: String): HttpStatusCode = when (code) {
    "E_AUTH" -> HttpStatusCode.Unauthorized
    "E_SCOPE" -> HttpStatusCode.Forbidden
    "E_TENANT" -> HttpStatusCode.NotFound
    "E_CONFLICT" -> HttpStatusCode.Conflict
    "E_RATE" -> HttpStatusCode.TooManyRequests
    else -> HttpStatusCode.BadRequest
}
