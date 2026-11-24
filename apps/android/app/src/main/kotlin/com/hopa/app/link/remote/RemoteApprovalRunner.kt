package com.hopa.app.link.remote

import com.hopa.approval.ApprovalAnswer
import com.hopa.backend.link.BackendLink
import com.hopa.backend.wire.BackendEnvelope
import com.hopa.backend.wire.BackendReplies
import com.hopa.contracts.a2ui.A2uiCodec
import com.hopa.contracts.a2ui.A2uiSurface
import com.hopa.contracts.a2ui.ApprovalCard
import com.hopa.contracts.a2ui.ApprovalSurfaces
import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.contracts.json.HopaJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject

/**
 * Shows an approval asked for by another device and sends the answer back.
 *
 * The request is a `task.needs_approval` frame from the PC. It never runs a
 * goal and never touches the screen: the person's decision is the whole task.
 * The socket stays open while the sheet waits, and a timeout is a denial.
 *
 * ADR-0027: when the frame carries an `a2ui` card, the card is checked with
 * `ApprovalSurfaces.receive` (A2UI rules, approval rules, and that it shows
 * the typed request) and drawn as sent. A card that fails is never drawn: the
 * answer is deny, with the A2UI `error`. Allow then counts only with the
 * `approve` tap from that card, which travels back as `approval.action`.
 * A frame without `a2ui` keeps the old path.
 */
class RemoteApprovalRunner(
    private val deviceId: () -> String,
    private val link: () -> BackendLink?,
    private val ask: suspend (ApprovalRequest, A2uiSurface?) -> ApprovalAnswer,
    private val foreground: () -> Boolean,
    private val postApproval: () -> Unit,
    private val taskStarted: () -> Unit = {},
    private val taskFinished: () -> Unit = {},
    private val timeoutMillis: Long = 90_000,
) {
    private val gate = Mutex()

    suspend fun handle(task: BackendEnvelope) {
        val live = link() ?: return
        // Ack first: a locked or busy phone still has to tell the PC the
        // request arrived before it starts waiting for a person.
        live.ack(task)
        val id = task.task.orEmpty().trim()
        val payload = task.body["approval"] as? JsonObject
        val request = payload?.let(::decode)
        if (id.isBlank() || request == null) {
            live.send(
                BackendReplies.error(
                    task,
                    deviceId(),
                    "E_SCHEMA",
                    "the approval request had no task id or no approval body",
                    false,
                ),
            )
            return
        }
        val card = task.body["a2ui"]?.let { ApprovalSurfaces.receive(it, request) }
        if (card is ApprovalCard.Refused) {
            // Nothing is drawn, so nobody was asked: the answer is deny.
            live.send(
                BackendReplies.approval(task, deviceId(), false, error = card.error?.let(A2uiCodec::encodeReply)),
            )
            return
        }
        val surface = (card as? ApprovalCard.Shown)?.surface

        gate.withLock {
            taskStarted()
            try {
                if (!foreground()) {
                    // The sheet can only be answered in HOPA; the notification
                    // is the way back to it while another app is in front.
                    runCatching { postApproval() }
                }
                val answer = try {
                    withTimeoutOrNull(timeoutMillis) { ask(request, surface) } ?: ApprovalAnswer.DENY
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    ApprovalAnswer.DENY
                }
                live.send(reply(task, surface, answer))
            } finally {
                taskFinished()
            }
        }
    }

    /** With a card, allow needs this card's approve tap; the tap goes back so the PC can check it too. */
    private fun reply(task: BackendEnvelope, surface: A2uiSurface?, answer: ApprovalAnswer): BackendEnvelope {
        if (surface == null) return BackendReplies.approval(task, deviceId(), answer.allow)
        val tap = answer.action?.takeIf { ApprovalSurfaces.allowed(answer.allow, it, surface.id) }
        return BackendReplies.approval(task, deviceId(), tap != null, action = tap?.let(A2uiCodec::encodeReply))
    }

    private fun decode(payload: JsonObject): ApprovalRequest? = runCatching {
        HopaJson.element.decodeFromJsonElement(ApprovalRequest.serializer(), payload)
    }.getOrNull()
}
