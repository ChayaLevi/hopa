package com.hopa.backend.admin

import com.hopa.backend.ServerConfig
import com.hopa.backend.http.BodyTooLarge
import com.hopa.backend.http.receiveBounded
import com.hopa.backend.http.respondError
import com.hopa.backend.store.DeviceInventory
import com.hopa.backend.tasks.RouteResult
import com.hopa.backend.tasks.TaskRouter
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.call
import io.ktor.server.request.header
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** One device as the admin inventory renders it; presence comes from the registry. */
@Serializable
data class AdminDeviceView(
    val id: String,
    val platform: String,
    val name: String,
    val lastSeenAt: String? = null,
    val revokedAt: String? = null,
    val online: Boolean,
)

/** The diagnostic request names its household explicitly (R26). */
@Serializable
data class DiagRequest(
    val household: String,
    val type: String,
    val body: JsonObject? = null,
)

@Serializable
data class DiagAccepted(val taskId: String, val trace: String, val state: String)

private val UUID_PATTERN =
    Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/**
 * The operations console: the household's device inventory and the diagnostic
 * sender. It can list devices and send one of three plaintext diagnostics; it
 * never reads a household's tasks or turns.
 */
fun Application.adminRoutes(config: ServerConfig) {
    val inventory = config.devices as? DeviceInventory

    routing {
        get("/v1/admin/devices") {
            call.adminIdentity(config) ?: return@get
            val raw = call.request.queryParameters["household"]
            val household = raw?.takeIf { UUID_PATTERN.matches(it) }?.let { UUID.fromString(it).toString() }
            if (household == null) {
                call.respondError(
                    HttpStatusCode.BadRequest,
                    "E_SCHEMA",
                    "household must be a uuid",
                )
                return@get
            }
            if (inventory == null) {
                call.respondError(
                    HttpStatusCode.ServiceUnavailable,
                    "E_UNSUPPORTED",
                    "device inventory is not configured",
                )
                return@get
            }
            val devices = inventory.list(household).map { row ->
                AdminDeviceView(
                    id = row.id,
                    platform = row.platform,
                    name = row.name,
                    lastSeenAt = row.lastSeenAt,
                    revokedAt = row.revokedAt,
                    online = config.registry.online(row.id),
                )
            }
            call.respond(HttpStatusCode.OK, devices)
        }

        post("/v1/admin/devices/{id}/diag") {
            val admin = call.adminIdentity(config) ?: return@post
            val rawId = call.parameters["id"].orEmpty()
            val deviceId = rawId
                .takeIf { UUID_PATTERN.matches(it) }
                ?.let { UUID.fromString(it).toString() }
            if (deviceId == null) {
                call.respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "device id must be a uuid")
                return@post
            }
            val request = try {
                call.receiveBounded(DiagRequest.serializer())
            } catch (_: BodyTooLarge) {
                call.respondError(
                    HttpStatusCode.PayloadTooLarge,
                    "E_SCHEMA",
                    "request body is larger than 64 KiB",
                )
                return@post
            } catch (_: Exception) {
                call.respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "invalid JSON body")
                return@post
            }
            val household = request.household
                .takeIf { UUID_PATTERN.matches(it) }
                ?.let { UUID.fromString(it).toString() }
            if (household == null) {
                call.respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "household must be a uuid")
                return@post
            }

            // The device lookup is also the tenancy check: it only sees the
            // named household and hides a revoked device, so both answer the
            // same refusal and no row is written.
            val target = config.devices.lookup(deviceId, household)
            // The directory is already tenant-scoped; comparing the returned
            // household is a second, independent guard against a future
            // directory implementation drifting from that contract.
            if (target == null || target.householdId != household) {
                call.respondError(
                    HttpStatusCode.Forbidden,
                    "E_TENANT",
                    "no such live device in this household",
                )
                return@post
            }

            val router = TaskRouter(
                store = config.tasks,
                registry = config.registry,
                devices = config.devices,
                log = config.log,
                push = config.push,
                traces = config.traces,
            )
            when (
                val routed =
                    router.createServerTask(admin.adminId, target, request.type, request.body ?: JsonObject(emptyMap()))
            ) {
                is RouteResult.Accepted -> call.respond(
                    HttpStatusCode.Accepted,
                    DiagAccepted(
                        taskId = routed.task.id,
                        trace = routed.task.trace,
                        state = routed.task.state,
                    ),
                )

                is RouteResult.Refused -> call.respondError(
                    HttpStatusCode.BadRequest,
                    routed.error.code,
                    routed.error.message,
                )
            }
        }
    }
}

/** Resolves the admin bearer, answering 401 E_AUTH and returning null when it is unknown. */
private suspend fun ApplicationCall.adminIdentity(config: ServerConfig): AdminIdentity? {
    val token = request.header(HttpHeaders.Authorization)
        ?.removePrefix("Bearer ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
    val identity = token?.let { config.adminAuth?.adminFor(it) }
    if (identity == null) {
        respondError(HttpStatusCode.Unauthorized, "E_AUTH", "unknown or expired admin token")
    }
    return identity
}
