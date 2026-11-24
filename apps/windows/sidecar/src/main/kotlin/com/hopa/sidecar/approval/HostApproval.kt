package com.hopa.sidecar.approval

import com.hopa.contracts.a2ui.ApprovalSurfaces
import com.hopa.contracts.approval.ApprovalDecision
import com.hopa.contracts.approval.ApprovalGateway
import com.hopa.contracts.approval.ApprovalRequest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonElement
import com.hopa.sidecar.protocol.Protocol
import com.hopa.sidecar.store.PendingApprovals
import com.hopa.sidecar.protocol.Stdio

/**
 * W2: approvals travel to the window and wait up to two minutes.
 *
 * ADR-0027: each question also carries its A2UI card, built here from the
 * typed request, so a phone can draw it. The answer is checked here, not in
 * the window: an A2UI tap that came back must agree with `allow` and name
 * this question's surface, or the answer is deny.
 */
class HostApproval(
    private val io: Stdio,
    private val store: PendingApprovals,
    private val timeoutMillis: Long = 120_000,
) : ApprovalGateway {
    private class Question(val surfaceId: String, val answer: CompletableDeferred<ApprovalDecision>)

    private val pending = ConcurrentHashMap<String, Question>()
    private val counter = AtomicInteger(0)

    /**
     * The host answers one question. `device` is "pc" for the local window and
     * "phone" when the answer came from Android over the gateway; the record keeps it.
     * [action] is the phone's raw `approval.action`; none means a phone without A2UI.
     */
    fun resolve(id: String, allow: Boolean, device: String = "pc", action: JsonElement? = null) {
        val question = pending.remove(id) ?: return
        val agreed = ApprovalSurfaces.answered(allow, action, question.surfaceId)
        if (allow && !agreed) io.log("approval $id: the tap did not agree with allow; deny")
        question.answer.complete(ApprovalDecision(agreed, device))
    }

    override suspend fun approve(request: ApprovalRequest): Boolean = decide(request).allow

    override suspend fun decide(request: ApprovalRequest): ApprovalDecision {
        val id = "ap-${counter.incrementAndGet()}"
        // Unique across restarts too: a stale tap for an earlier "ap-1" names another surface.
        val surfaceId = ApprovalSurfaces.surfaceId("${id}_${UUID.randomUUID().toString().take(8)}", 1)
        val deferred = CompletableDeferred<ApprovalDecision>()
        pending[id] = Question(surfaceId, deferred)
        store.save(id, request)
        io.send(Protocol.approvalRequest(id, request, ApprovalSurfaces.wire(request, surfaceId)))
        // A missing or late answer is a deny from the PC side (protocol v1).
        val answer = withTimeoutOrNull(timeoutMillis) { deferred.await() }
            ?: ApprovalDecision(false, "pc")
        pending.remove(id)
        store.clear(id)
        return answer
    }
}
