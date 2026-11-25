package com.hopa.app.link.remote

import com.hopa.backend.approval.AnswerSigning
import com.hopa.backend.approval.ApprovalChallenge
import com.hopa.backend.approval.SignedAnswer
import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.stores.SettingsRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import java.util.Base64

/**
 * This phone's approval signing key (ADR-0028). Created on first use and kept
 * in the encrypted settings store; it never leaves the phone. The PC pins its
 * public half the first time this phone answers.
 */
class ApprovalSigner(private val settings: SettingsRepository) {
    private val lock = Mutex()

    /** `body.approval.signature` for [allow] on the question [challenge] names. */
    suspend fun sign(
        challenge: ApprovalChallenge,
        request: ApprovalRequest,
        surfaceId: String?,
        allow: Boolean,
    ): JsonObject = SignedAnswer.sign(seed(), challenge, surfaceId, request, allow).toJson()

    /** What the person compares with the PC's log: four groups of four hex digits. */
    suspend fun fingerprint(): String = AnswerSigning.fingerprint(AnswerSigning.publicKey(seed()))

    private suspend fun seed(): ByteArray = lock.withLock {
        settings.approvalSigningKey()
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }
            ?.takeIf { it.size == AnswerSigning.SEED_BYTES }
            ?: AnswerSigning.newSeed().also { settings.setApprovalSigningKey(Base64.getEncoder().encodeToString(it)) }
    }
}
