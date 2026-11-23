package com.hopa.backend.tasks

import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.gateway.DeviceRegistry
import com.hopa.backend.log.EventLog
import com.hopa.backend.protocol.Envelope
import com.hopa.backend.protocol.EnvelopeValidator
import com.hopa.backend.protocol.GatewayCodec
import com.hopa.backend.protocol.GatewayError
import com.hopa.backend.push.PushOutcome
import com.hopa.backend.push.PushPort
import com.hopa.backend.trace.TraceIds
import com.hopa.backend.trace.TraceRecorder
import java.util.UUID
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Every paired device, online or not. Production resolves from Postgres. */
interface DeviceDirectory {
    fun lookup(deviceId: String, householdId: String): DeviceIdentity?

    /** The FCM registration, or null when the device has none. */
    fun pushToken(deviceId: String, householdId: String): String? = null

    /** Records presence and, when present, the latest push token. */
    fun touch(deviceId: String, householdId: String, pushToken: String?, at: Long) {}

    /** An unregistered FCM token is cleared until the next hello replaces it. */
    fun clearPushToken(deviceId: String, householdId: String) {}

    companion object {
        val Empty = object : DeviceDirectory {
            override fun lookup(deviceId: String, householdId: String): DeviceIdentity? = null
        }
    }
}

sealed interface RouteResult {
    data class Accepted(val task: TaskRecord, val duplicate: Boolean) : RouteResult

    data class Refused(val error: GatewayError, val trace: String?) : RouteResult
}

/**
 * Routes one message. Idempotency is checked first, tenancy second: a replayed
 * message never re-routes, and a target outside the household is refused before
 * any row is written.
 */
class TaskRouter(
    private val store: TaskStore,
    private val registry: DeviceRegistry,
    private val devices: DeviceDirectory,
    private val log: EventLog = EventLog.None,
    private val push: PushPort = PushPort.None,
    private val traces: TraceRecorder = TraceRecorder.None,
    private val newTaskId: () -> String = { UUID.randomUUID().toString() },
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val deadlineMs = 10 * 60 * 1000L
    private val maxDeadlineMs = 24L * 60 * 60 * 1000

    private companion object {
        /** The sender id a server-authored row carries instead of a device. */
        const val SERVER = "server"

        /** The only payloads that are plaintext by type; nothing else is routable here. */
        val DIAG_TYPES = setOf("diag.ping", "diag.state", "diag.reset")

        /** Half the frame cap, leaving room for the envelope fields around it. */
        const val MAX_DIAG_BODY_BYTES = 32 * 1024

        /** A result, error or cancel ends the task; nothing may move it again. */
        val TERMINAL_STATES = setOf("done", "failed", "cancelled")

        /** Late delivery signals that must not reopen a terminal task. */
        val POST_TERMINAL_TYPES = setOf("task.ack", "task.progress")
    }

    fun route(
        identity: DeviceIdentity,
        envelope: Envelope,
        traceparent: String? = null,
    ): RouteResult {
        val trace = traceparent ?: EnvelopeValidator.traceOf(envelope)
        return traces.span(
            identity.householdId,
            traceId(trace),
            "route",
            identity.deviceId,
            envelope.task,
        ) {
            routeEnvelope(identity, envelope, trace)
        }
    }

    /** Deliveries owed to a device that just came online, or never acked one. */
    fun deliverPending(deviceId: String, householdId: String): List<TaskRecord> {
        val due = store.pendingFor(deviceId, householdId, now())
        due.forEach { task -> deliver(task) }
        return due
    }

    /** How much work a device would find if it came online right now. */
    fun queuedFor(deviceId: String, householdId: String): Int =
        store.pendingFor(deviceId, householdId, now()).size

    /**
     * A task the server authors itself: an admin diagnostic. The target's
     * household and member come from the looked-up device, [type] is confined
     * to the plaintext diagnostic types, and the row, event, delivery or wake
     * follow exactly the [create] path. Only identifiers reach the audit log.
     */
    fun createServerTask(
        adminId: String,
        target: DeviceIdentity,
        type: String,
        body: JsonObject,
    ): RouteResult {
        if (type !in DIAG_TYPES) {
            return RouteResult.Refused(GatewayError("E_SCHEMA", "not a diagnostic type"), null)
        }
        // The body becomes the delivered frame's body; cap it well under the
        // frame limit so the envelope's own fields can never push it over.
        if (body.toString().toByteArray(Charsets.UTF_8).size > MAX_DIAG_BODY_BYTES) {
            return RouteResult.Refused(
                GatewayError("E_SCHEMA", "diagnostic body is larger than $MAX_DIAG_BODY_BYTES bytes"),
                null,
            )
        }

        val trace = TraceIds.newTraceparent()
        val task = TaskRecord(
            id = newTaskId(),
            householdId = target.householdId,
            memberId = target.memberId,
            from = SERVER,
            to = target.deviceId,
            messageId = UUID.randomUUID().toString(),
            trace = trace,
            ts = now(),
            body = body,
            state = if (registry.online(target.deviceId)) "delivered" else "queued",
            deadlineAt = now() + deadlineMs,
            type = type,
        )
        if (!store.remember(task.householdId, task.messageId, task)) {
            return RouteResult.Refused(
                GatewayError("E_QUEUE", "diagnostic task could not be queued"),
                trace,
            )
        }
        store.event(task.id, task.householdId, "create", body, now())

        // The audit is written as soon as the row is durable and before the
        // delivery or wake: a push failure after this point must not leave a
        // task whose only admin linkage never reached the log.
        log.event(
            "diag.send",
            mapOf(
                "admin_id" to adminId,
                "household_id" to task.householdId,
                "device_id" to task.to,
                "task_id" to task.id,
                "trace" to trace,
            ),
        )

        val routed = if (registry.online(target.deviceId)) {
            deliver(task)
        } else {
            try {
                wake(task)
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (failed: Exception) {
                // The row and audit are already durable. A dead push service
                // must not fail the admin's 202: the hello catch-up delivers it.
                log.event(
                    "diag.wake.failed",
                    mapOf(
                        "admin_id" to adminId,
                        "household_id" to task.householdId,
                        "device_id" to task.to,
                        "task_id" to task.id,
                    ),
                )
            }
            task
        }
        return RouteResult.Accepted(routed, duplicate = false)
    }

    private fun routeEnvelope(
        identity: DeviceIdentity,
        envelope: Envelope,
        traceparent: String,
    ): RouteResult {
        store.recall(identity.householdId, envelope.id)?.let { replayed ->
            return RouteResult.Accepted(replayed, duplicate = true)
        }

        return when (envelope.type) {
            "task.create" -> create(identity, envelope, traceparent)
            "task.needs_approval" ->
                create(identity, envelope, traceparent, state = "needs_approval", event = "task.needs_approval")
            "task.ack", "task.progress", "task.result", "task.error", "task.cancel" ->
                relay(identity, envelope)

            else -> RouteResult.Refused(
                GatewayError("E_UNSUPPORTED", "not routable in v0: ${envelope.type}"),
                envelope.trace,
            )
        }
    }

    private fun create(
        identity: DeviceIdentity,
        envelope: Envelope,
        traceparent: String,
        state: String = "",
        event: String = "create",
    ): RouteResult {
        val to = envelope.to
            ?: return RouteResult.Refused(GatewayError("E_SCHEMA", "a task needs a target"), envelope.trace)

        val target = devices.lookup(to, identity.householdId)
        if (target == null) {
            log.event(
                "tenant_refused",
                mapOf(
                    "household_id" to identity.householdId,
                    "device_id" to identity.deviceId,
                    "to" to to,
                ),
            )
            return RouteResult.Refused(
                GatewayError("E_TENANT", "no such device in this household"),
                envelope.trace,
            )
        }

        val task = TaskRecord(
            id = newTaskId(),
            householdId = identity.householdId,
            memberId = identity.memberId,
            from = identity.deviceId,
            to = to,
            messageId = envelope.id,
            trace = traceparent,
            ts = envelope.ts,
            body = envelope.body,
            state = state.ifEmpty { if (registry.online(to)) "delivered" else "queued" },
            deadlineAt = deadlineFrom(envelope.body),
        )
        if (!store.remember(identity.householdId, envelope.id, task)) {
            val existing = store.recall(identity.householdId, envelope.id)
                ?: return RouteResult.Refused(
                    GatewayError("E_QUEUE", "duplicate message could not be read back"),
                    envelope.trace,
                )
            return RouteResult.Accepted(existing, duplicate = true)
        }
        store.event(task.id, identity.householdId, event, envelope.body, now())

        val routed = if (registry.online(to)) {
            deliver(task)
        } else {
            wake(task)
            task
        }
        return RouteResult.Accepted(routed, duplicate = false)
    }

    /** An offline target gets one data-only wake when it has a registration. */
    private fun wake(task: TaskRecord) {
        val pushToken = devices.pushToken(task.to, task.householdId) ?: return
        val outcome = traces.span(
            task.householdId,
            traceId(task.trace),
            "wake",
            spanDevice(task),
            task.id,
        ) {
            push.wake(task.to, task.id, traceId(task.trace), pushToken)
        }
        if (outcome == PushOutcome.InvalidToken) {
            devices.clearPushToken(task.to, task.householdId)
        }
    }

    private fun relay(identity: DeviceIdentity, envelope: Envelope): RouteResult {
        val taskId = envelope.task
            ?: return RouteResult.Refused(GatewayError("E_SCHEMA", "missing task id"), envelope.trace)
        val task = store.find(taskId, identity.householdId)
            ?: return RouteResult.Refused(GatewayError("E_SCHEMA", "unknown task"), envelope.trace)
        if (identity.deviceId != task.from && identity.deviceId != task.to) {
            return RouteResult.Refused(GatewayError("E_TENANT", "not your task"), envelope.trace)
        }

        if (envelope.type == "task.ack") {
            traces.span(
                identity.householdId,
                traceId(task.trace),
                "ack",
                identity.deviceId,
                task.id,
            ) {}
        }

        // A client may ack after its result (the sidecar sends result then ack).
        // Terminal means terminal: record the event, but never move the state
        // back to acked/progress or relay a stale signal.
        if (task.state in TERMINAL_STATES && envelope.type in POST_TERMINAL_TYPES) {
            store.event(task.id, identity.householdId, envelope.type, envelope.body, now())
            return RouteResult.Accepted(task, duplicate = false)
        }

        val next = when (envelope.type) {
            "task.ack" -> task.copy(state = "acked")
            "task.progress" -> task.copy(state = "progress")
            "task.result" -> task.copy(
                state = if (envelope.body.approvalAllowed() == false) "failed" else "done",
            )
            "task.error" -> task.copy(state = "failed")
            "task.cancel" -> task.copy(state = "cancelled")
            else -> task
        }
        // Record before the frame leaves: an ack the sender receives must already
        // be durable, or a reconnect can see the task as undelivered and send it
        // again while the client believes it was acked.
        if (next != task) {
            store.update(next)
        }
        store.event(task.id, identity.householdId, envelope.type, envelope.body, now())

        val other = if (identity.deviceId == task.from) task.to else task.from
        // A server-authored task has no sending socket to relay the result to;
        // the row and its event are the whole record.
        val target = if (other == SERVER) null else registry.sessionFor(other)
        val relayed = target?.outgoing?.trySend(
            GatewayCodec.encode(
                envelope.copy(
                    task = task.id,
                    from = identity.deviceId,
                    to = other,
                    // The relay joins the task's chain; a frame from a client that
                    // sent no traceparent must not start a new one.
                    trace = task.trace,
                ),
            ),
        )?.isSuccess == true
        if (!relayed && target != null) {
            // The socket is gone but still marked online; drop it so the next
            // create queues instead of vanishing into a closed channel.
            registry.leave(target)
        }

        return RouteResult.Accepted(next, duplicate = false)
    }

    private fun deliver(task: TaskRecord): TaskRecord =
        traces.span(
            task.householdId,
            traceId(task.trace),
            "deliver",
            spanDevice(task),
            task.id,
        ) {
            val session = registry.sessionFor(task.to) ?: return@span task
            // The hello catch-up runs after the hello ack, so it can see a task
            // `create` has just delivered on this same socket. Sending it again
            // would duplicate the frame; a reconnect gets a fresh session, so an
            // unacked task is still redelivered then.
            if (!session.sent.add(task.id)) return@span task
            // An approval request is its own frame type; a server diagnostic
            // keeps the type it was authored with; a normal handoff is still
            // `task.create`. The state survives delivery so a reconnect can send
            // the same request again until the target acks it.
            val type = if (task.state == "needs_approval") "task.needs_approval" else task.type
            val sent = session.outgoing.trySend(
                GatewayCodec.encode(
                    Envelope(
                        v = 1,
                        id = task.messageId,
                        type = type,
                        ts = task.ts,
                        trace = task.trace,
                        from = task.from,
                        to = task.to,
                        task = task.id,
                        body = task.body,
                    ),
                ),
            ).isSuccess
            if (!sent) {
                // A closed socket must not stay "online": the task is still
                // queued, but the next hello is what will deliver it.
                registry.leave(session)
                return@span task
            }
            if (task.state == "needs_approval") {
                store.event(task.id, task.householdId, "deliver", JsonObject(emptyMap()), now())
                return@span task
            }
            store.markDelivered(task.id, task.householdId)
            store.event(task.id, task.householdId, "deliver", JsonObject(emptyMap()), now())
            task.copy(state = "delivered")
        }

    /**
     * `trace_spans.device_id` is a uuid, so a server-authored task's span must
     * name the target device; the literal "server" would make the insert fail
     * and the trace silently disappear.
     */
    private fun spanDevice(task: TaskRecord): String =
        if (task.from == SERVER) task.to else task.from

    /** Null when the result is not an approval; false is a denial. */
    private fun JsonObject.approvalAllowed(): Boolean? =
        (this["approval"] as? JsonObject)
            ?.get("allow")
            ?.jsonPrimitive
            ?.booleanOrNull

    private fun traceId(traceparent: String): String =
        TraceIds.traceId(traceparent) ?: TraceIds.traceId(TraceIds.newTraceparent())!!

    /**
     * A sender may ask for a longer or shorter deadline in the body. Anything
     * outside (now, now + 24 h] is treated as absent rather than as an
     * undeliverable task.
     */
    private fun deadlineFrom(body: JsonObject): Long {
        val nowMs = now()
        val requested = body["deadlineAt"]?.jsonPrimitive?.longOrNull
        return if (requested != null && requested > nowMs && requested <= nowMs + maxDeadlineMs) {
            requested
        } else {
            nowMs + deadlineMs
        }
    }
}
