package com.hopa.backend.approval

import com.hopa.contracts.approval.ApprovalRequest
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * The bytes an approval answer is signed over (ADR-0032). Every field is
 * UTF-8, prefixed by its length as 4 big-endian bytes, so no client has to
 * agree on JSON formatting and no two different answers share the bytes:
 *
 * `"hopa.approval.answer.v1" | nonce | expiresAt | surfaceId | actionHash | decision`
 *
 * `decision` is `allow` or `deny`. `surfaceId` is empty when no card was sent.
 * `actionHash` ([actionHash]) pins what was asked: change one character of the
 * amount and the answer no longer verifies. `spec/fixtures/approval-signatures.json`
 * holds the vectors every client must reproduce.
 */
object SignedAnswer {
    const val DOMAIN = "hopa.approval.answer.v1"
    const val ACTION_DOMAIN = "hopa.approval.action.v1"

    /** Lowercase hex SHA-256 of the fields of [request] a person consents to. */
    fun actionHash(request: ApprovalRequest): String = sha256Hex(
        fields(
            ACTION_DOMAIN,
            request.title,
            request.body,
            request.amount,
            request.merchant,
            request.consequence,
            request.isPayment.toString(),
            request.action.kind,
        ),
    )

    fun message(
        challenge: ApprovalChallenge,
        surfaceId: String?,
        request: ApprovalRequest,
        allow: Boolean,
    ): ByteArray = fields(
        DOMAIN,
        challenge.nonce,
        challenge.expiresAt.toString(),
        surfaceId.orEmpty(),
        actionHash(request),
        if (allow) "allow" else "deny",
    )

    fun sign(
        seed: ByteArray,
        challenge: ApprovalChallenge,
        surfaceId: String?,
        request: ApprovalRequest,
        allow: Boolean,
    ): AnswerSignature = AnswerSignature(
        key = AnswerSigning.publicKey(seed),
        sig = AnswerSigning.sign(seed, message(challenge, surfaceId, request, allow)),
    )

    /** True only when [signature] is over exactly this question and this decision. */
    fun verify(
        signature: AnswerSignature,
        challenge: ApprovalChallenge,
        surfaceId: String?,
        request: ApprovalRequest,
        allow: Boolean,
    ): Boolean = AnswerSigning.verify(signature.key, message(challenge, surfaceId, request, allow), signature.sig)

    internal fun fields(vararg values: String): ByteArray = ByteArrayOutputStream().apply {
        values.forEach { value ->
            val bytes = value.toByteArray(Charsets.UTF_8)
            write(bytes.size ushr 24)
            write(bytes.size ushr 16)
            write(bytes.size ushr 8)
            write(bytes.size)
            write(bytes)
        }
    }.toByteArray()

    private fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
