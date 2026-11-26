package com.hopa.backend.members

import com.hopa.backend.ServerConfig
import com.hopa.backend.http.BodyTooLarge
import com.hopa.backend.http.bearerDevice
import com.hopa.backend.http.receiveBounded
import com.hopa.backend.http.respondError
import com.hopa.backend.http.statusFor
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.put
import io.ktor.server.routing.routing
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable

@Serializable
internal class MembersResponse(val members: List<GroupMember>)

@Serializable
internal class PermissionsRequest(val permissions: List<String>)

/**
 * The group's members and their permissions (V7). Any device of the group
 * reads the list; only a member holding `group.manage` sets a member's
 * permissions. Clients show `board.private` as "parent". The household always
 * comes from the bearer token, so a member of another group answers 404
 * `E_TENANT`.
 */
fun Application.memberRoutes(
    config: ServerConfig,
    service: MemberService = MemberService(config.members, config.registry),
) {
    routing {
        get("/v1/members") {
            val identity = call.bearerDevice(config) ?: return@get
            call.respond(MembersResponse(service.list(identity)))
        }

        put("/v1/members/{id}/permissions") {
            val identity = call.bearerDevice(config) ?: return@put
            val body = try {
                call.receiveBounded(PermissionsRequest.serializer())
            } catch (_: BodyTooLarge) {
                call.respondError(HttpStatusCode.PayloadTooLarge, "E_SCHEMA", "body is too large")
                return@put
            } catch (_: SerializationException) {
                call.respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "body needs permissions")
                return@put
            } catch (_: IllegalArgumentException) {
                call.respondError(HttpStatusCode.BadRequest, "E_SCHEMA", "body needs permissions")
                return@put
            }
            when (val outcome = service.change(identity, call.parameters["id"].orEmpty(), body.permissions)) {
                is PermissionOutcome.Changed -> call.respond(outcome.member)
                is PermissionOutcome.Refused -> call.respondError(
                    statusFor(outcome.error.code),
                    outcome.error.code,
                    outcome.error.message,
                )
            }
        }
    }
}
