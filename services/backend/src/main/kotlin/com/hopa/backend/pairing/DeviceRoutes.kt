package com.hopa.backend.pairing

import com.hopa.backend.ServerConfig
import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.auth.TokenCacheInvalidator
import com.hopa.backend.http.respondError
import com.hopa.backend.store.DeviceInventory
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class DeviceView(
    val id: String,
    val platform: String,
    val name: String,
    val lastSeenAt: String? = null,
    val revokedAt: String? = null,
    val current: Boolean,
)

private val UUID_PATTERN =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/** The household's paired devices, and immediate revocation of one of them. */
fun Application.deviceRoutes(config: ServerConfig) {
    val inventory = config.devices as? DeviceInventory

    routing {
        get("/v1/devices") {
            val identity = call.bearerIdentity(config) ?: return@get
            if (inventory == null) {
                call.respondError(
                    HttpStatusCode.ServiceUnavailable,
                    "E_UNSUPPORTED",
                    "device inventory is not configured",
                )
                return@get
            }
            val devices = inventory.list(identity.householdId).map { row ->
                DeviceView(
                    id = row.id,
                    platform = row.platform,
                    name = row.name,
                    lastSeenAt = row.lastSeenAt,
                    revokedAt = row.revokedAt,
                    current = row.id == identity.deviceId,
                )
            }
            call.respond(HttpStatusCode.OK, devices)
        }

        delete("/v1/devices/{id}") {
            val identity = call.bearerIdentity(config) ?: return@delete
            val raw = call.parameters["id"].orEmpty()
            val deviceId = if (UUID_PATTERN.matches(raw)) {
                UUID.fromString(raw).toString()
            } else {
                null
            }
            if (deviceId == null) {
                call.respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "device id must be a uuid")
                return@delete
            }
            if (inventory == null) {
                call.respondError(
                    HttpStatusCode.ServiceUnavailable,
                    "E_UNSUPPORTED",
                    "device inventory is not configured",
                )
                return@delete
            }
            if (!inventory.revokeDevice(deviceId, identity.householdId)) {
                call.respondError(
                    HttpStatusCode.NotFound,
                    "E_TENANT",
                    "no such device in this household",
                )
                return@delete
            }

            // The transaction is committed: drop the cached token identity and
            // close the live socket if the device is connected on this process.
            (config.auth as? TokenCacheInvalidator)?.invalidate(deviceId)
            config.registry.kick(deviceId)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}

/** Resolves the bearer, answering 401 E_AUTH and returning null when it is unknown. */
private suspend fun ApplicationCall.bearerIdentity(config: ServerConfig): DeviceIdentity? {
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
