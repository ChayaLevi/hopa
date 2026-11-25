package com.hopa.backend.approval

import com.hopa.contracts.approval.ApprovalRequest
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull

/**
 * How the asking device reads a signed answer (ADR-0032). It only ever turns
 * an allow into a deny; a deny needs no proof. Keys are pinned on first use:
 * the first verified answer from a device fixes its key, and from then on
 * that device's allow must carry a signature from that key.
 */
object AnswerCheck {
    /**
     * [signature] is the raw `body.approval.signature`, [from] the device the
     * gateway says sent the answer, [now] the asker's clock in epoch ms.
     */
    fun check(
        allow: Boolean,
        signature: JsonElement?,
        from: String,
        challenge: ApprovalChallenge,
        surfaceId: String?,
        request: ApprovalRequest,
        now: Long,
        pins: KeyPins,
    ): AnswerVerdict {
        if (!allow) return AnswerVerdict.Denied(AnswerVerdict.DENIED)
        if (now > challenge.expiresAt) return AnswerVerdict.Denied(AnswerVerdict.EXPIRED)
        val pinned = from.takeIf { it.isNotBlank() }?.let(pins::pinned)
        if (signature == null || signature is JsonNull) {
            return if (pinned != null) {
                AnswerVerdict.Denied(AnswerVerdict.UNSIGNED)
            } else {
                AnswerVerdict.Allowed(AnswerVerdict.LEGACY)
            }
        }
        val parsed = AnswerSignature.parse(signature) ?: return AnswerVerdict.Denied(AnswerVerdict.MALFORMED)
        if (from.isBlank()) return AnswerVerdict.Denied(AnswerVerdict.NO_SENDER)
        if (!SignedAnswer.verify(parsed, challenge, surfaceId, request, allow = true)) {
            return AnswerVerdict.Denied(AnswerVerdict.BAD_SIGNATURE)
        }
        return when {
            pinned == null -> {
                pins.pin(from, parsed.key)
                AnswerVerdict.Allowed(AnswerVerdict.PINNED)
            }
            pinned.contentEquals(parsed.key) -> AnswerVerdict.Allowed(AnswerVerdict.VERIFIED)
            else -> AnswerVerdict.Denied(AnswerVerdict.KEY_CHANGED)
        }
    }
}
