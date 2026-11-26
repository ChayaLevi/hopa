package com.hopa.backend.board

import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.gateway.DeviceRegistry
import com.hopa.backend.log.EventLog
import com.hopa.backend.protocol.Envelope
import com.hopa.backend.protocol.EnvelopeValidator
import com.hopa.backend.protocol.GatewayCodec
import com.hopa.backend.protocol.GatewayError
import com.hopa.backend.push.PushOutcome
import com.hopa.backend.push.PushPort
import com.hopa.backend.tasks.DeviceDirectory
import com.hopa.backend.trace.TraceIds
import com.hopa.backend.trace.TraceRecorder
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.put

/**
 * The gateway side of the family board (ADR-0033): one `board.op` frame in,
 * the sender's answer out, and the news to everyone else who may read it.
 *
 * After a new op is stored, every other online device of the household that
 * may read the scope gets `board.changed {scope, seq}` on its socket, and every
 * offline one that may read it and has a push token gets a board wake. The
 * sender gets neither; a duplicate tells nobody, because the first append
 * already did. Who may read is [BoardService.mayRead], so a teen device hears
 * nothing about a `parents` op.
 */
class BoardGateway(
    private val service: BoardService,
    private val registry: DeviceRegistry,
    private val devices: DeviceDirectory,
    private val push: PushPort = PushPort.None,
    private val traces: TraceRecorder = TraceRecorder.None,
    private val log: EventLog = EventLog.None,
    private val now: () -> Long = System::currentTimeMillis,
) {
    /** Appends the op a `board.op` frame carries; the envelope id is the op id. */
    fun append(identity: DeviceIdentity, envelope: Envelope, traceparent: String): BoardResult<BoardAck> {
        val body = envelope.body
        val scope = (body["scope"] as? JsonPrimitive)?.contentOrNull
        val keyEpoch = (body["keyEpoch"] as? JsonPrimitive)?.intOrNull
        val ciphertext = (body["ct"] as? JsonPrimitive)?.contentOrNull?.let(EnvelopeValidator::decodeCiphertext)
        if (scope == null || keyEpoch == null || ciphertext == null) {
            // The validator already refused these; this guards a caller that skipped it.
            return BoardResult.Failed(GatewayError("E_SCHEMA", "board.op needs scope, ct and keyEpoch"))
        }
        val traceId = TraceIds.traceId(traceparent) ?: TraceIds.traceId(TraceIds.newTraceparent())!!
        val result = traces.span(identity.householdId, traceId, "board.append", identity.deviceId, null) {
            service.append(identity, envelope.id, scope, keyEpoch, ciphertext)
        }
        if (result is BoardResult.Ok && !result.value.duplicate) {
            traces.span(identity.householdId, traceId, "board.fanout", identity.deviceId, null) {
                fanOut(identity, result.value, traceparent)
            }
        }
        return result
    }

    private fun fanOut(sender: DeviceIdentity, ack: BoardAck, traceparent: String) {
        val household = sender.householdId
        val online = registry.householdSessions(household)
            .filter { it.identity.deviceId != sender.deviceId }
        online
            .filter { service.mayRead(it.identity, ack.scope) }
            .forEach { session ->
                session.outgoing.trySend(GatewayCodec.encode(changedFrame(session.identity.deviceId, ack, traceparent)))
            }

        val reached = online.mapTo(HashSet()) { it.identity.deviceId } + sender.deviceId
        devices.householdDevices(household)
            .filter { it.deviceId !in reached && service.mayRead(it, ack.scope) }
            .forEach { device -> wake(device, ack) }
    }

    private fun wake(device: DeviceIdentity, ack: BoardAck) {
        val pushToken = devices.pushToken(device.deviceId, device.householdId) ?: return
        val outcome = try {
            push.wakeBoard(device.deviceId, ack.scope, ack.seq, pushToken)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // The op is stored and acked; a dead push service only delays this
            // device until its next hello or pull.
            log.event(
                "board.wake.failed",
                mapOf("household_id" to device.householdId, "device_id" to device.deviceId),
            )
            return
        }
        if (outcome == PushOutcome.InvalidToken) {
            devices.clearPushToken(device.deviceId, device.householdId)
        }
    }

    private fun changedFrame(to: String, ack: BoardAck, traceparent: String): Envelope = Envelope(
        v = 1,
        id = UUID.randomUUID().toString(),
        type = "board.changed",
        ts = now(),
        trace = traceparent,
        to = to,
        body = buildJsonObject {
            put("scope", ack.scope)
            put("seq", ack.seq)
        },
    )
}
