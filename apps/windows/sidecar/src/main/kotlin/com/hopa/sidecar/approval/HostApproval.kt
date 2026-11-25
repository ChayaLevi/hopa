package com.hopa.sidecar.approval

import com.hopa.backend.approval.AnswerCheck
import com.hopa.backend.approval.AnswerSignature
import com.hopa.backend.approval.AnswerSigning
import com.hopa.backend.approval.AnswerVerdict
import com.hopa.backend.approval.ApprovalChallenge
import com.hopa.backend.approval.KeyPins
import com.hopa.contracts.a2ui.ApprovalSurfaces
import com.hopa.contracts.approval.ApprovalDecision
import com.hopa.contracts.approval.ApprovalGateway
import com.hopa.contracts.approval.ApprovalRequest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import com.hopa.sidecar.protocol.Protocol
import com.hopa.sidecar.store.PendingApprovals
import com.hopa.sidecar.protocol.Stdio

/**
 * W2: approvals travel to the window and wait up to two minutes.
 *
 * Each question also carries what a phone needs to answer it safely, in
 * `relay`, which the window copies unchanged into the phone's request:
 * the A2UI card built here from the typed request (ADR-0027) and a fresh
 * challenge (ADR-0028). A phone's answer is checked here, not in the window:
 * its A2UI tap must agree with `allow` and name this card, and its signature
 * must be over this question and from the key pinned for that phone.
 * Anything else is deny.
 */
class HostApproval(
    private val io: Stdio,
    private val store: PendingApprovals,
    private val pins: KeyPins,
    private val timeoutMillis: Long = 120_000,
    private val now: () -> Long = System::currentTimeMillis,
) : ApprovalGateway {
    private class Question(
        val request: ApprovalRequest,
        val surfaceId: String,
        val challenge: ApprovalChallenge,
        val answer: CompletableDeferred<ApprovalDecision>,
    )

    private val pending = ConcurrentHashMap<String, Question>()
    private val counter = AtomicInteger(0)

    /**
     * The host answers one question. `device` is "pc" for the local window and
     * "phone" when the answer came from Android over the gateway; the record keeps it.
     * [phone] is what the phone sent: `from`, the gateway's sender, and its
     * `approval` object with `action` and `signature`. None means the window answered.
     */
    fun resolve(id: String, allow: Boolean, device: String = "pc", phone: JsonObject? = null) {
        val question = pending.remove(id) ?: return
        val agreed = allow && (device != "phone" || phoneAllowed(id, question, phone))
        question.answer.complete(ApprovalDecision(agreed, device))
    }

    override suspend fun approve(request: ApprovalRequest): Boolean = decide(request).allow

    override suspend fun decide(request: ApprovalRequest): ApprovalDecision {
        val id = "ap-${counter.incrementAndGet()}"
        // Unique across restarts too: a stale tap for an earlier "ap-1" names another surface.
        val surfaceId = ApprovalSurfaces.surfaceId("${id}_${UUID.randomUUID().toString().take(8)}", 1)
        val challenge = ApprovalChallenge.fresh(expiresAt = now() + timeoutMillis)
        val deferred = CompletableDeferred<ApprovalDecision>()
        pending[id] = Question(request, surfaceId, challenge, deferred)
        store.save(id, request)
        val relay = buildJsonObject {
            put("a2ui", ApprovalSurfaces.wire(request, surfaceId))
            put("challenge", challenge.toJson())
        }
        io.send(Protocol.approvalRequest(id, request, relay))
        // A missing or late answer is a deny from the PC side (protocol v1).
        val answer = withTimeoutOrNull(timeoutMillis) { deferred.await() }
            ?: ApprovalDecision(false, "pc")
        pending.remove(id)
        store.clear(id)
        return answer
    }

    private fun phoneAllowed(id: String, question: Question, phone: JsonObject?): Boolean {
        val approval = phone?.get("approval") as? JsonObject
        if (!ApprovalSurfaces.answered(true, approval?.get("action"), question.surfaceId)) {
            io.log("approval $id: the phone's tap did not agree with allow; deny")
            return false
        }
        val from = (phone?.get("from") as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
        val verdict = AnswerCheck.check(
            allow = true,
            signature = approval?.get("signature"),
            from = from,
            challenge = question.challenge,
            surfaceId = question.surfaceId,
            request = question.request,
            now = now(),
            pins = pins,
        )
        when (verdict.code) {
            AnswerVerdict.PINNED -> io.log("approval $id: pinned the key of phone $from: ${fingerprint(approval)}")
            AnswerVerdict.VERIFIED -> Unit
            AnswerVerdict.LEGACY -> io.log("approval $id: phone $from does not sign its answers yet")
            AnswerVerdict.KEY_CHANGED -> io.log(
                "approval $id: phone $from signed with another key (${fingerprint(approval)}); deny. " +
                    "If you paired the phone again, run hopa-sidecar --forget-phone-key",
            )
            else -> io.log("approval $id: the phone's signature was refused (${verdict.code}); deny")
        }
        return verdict is AnswerVerdict.Allowed
    }

    private fun fingerprint(approval: JsonObject?): String =
        AnswerSignature.parse(approval?.get("signature"))?.key?.let(AnswerSigning::fingerprint) ?: "?"
}
