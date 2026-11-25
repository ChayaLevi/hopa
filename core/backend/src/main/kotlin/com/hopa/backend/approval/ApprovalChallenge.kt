package com.hopa.backend.approval

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.security.SecureRandom
import java.util.Base64

/**
 * What the asking device sends with a `task.needs_approval` (ADR-0028) so the
 * answer can be bound to this one question: a fresh random [nonce] and the
 * time the question stops counting, [expiresAt] in epoch milliseconds. The
 * asker keeps both; an answer signed over anything else is deny.
 */
data class ApprovalChallenge(
    val nonce: String,
    val expiresAt: Long,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("v", VERSION)
        put("nonce", nonce)
        put("expiresAt", expiresAt)
    }

    companion object {
        const val VERSION = 1
        private const val NONCE_BYTES = 16
        private val NONCE = Regex("^[A-Za-z0-9_-]{16,64}$")
        private val random = SecureRandom()

        fun fresh(expiresAt: Long): ApprovalChallenge {
            val bytes = ByteArray(NONCE_BYTES).also(random::nextBytes)
            return ApprovalChallenge(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes), expiresAt)
        }

        /** The challenge in a request body, or null when it is missing or malformed. */
        fun parse(element: JsonElement?): ApprovalChallenge? {
            val obj = element as? JsonObject ?: return null
            if ((obj["v"] as? JsonPrimitive)?.longOrNull != VERSION.toLong()) return null
            val nonce = (obj["nonce"] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return null
            val expiresAt = (obj["expiresAt"] as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull ?: return null
            return ApprovalChallenge(nonce, expiresAt).takeIf { NONCE.matches(nonce) && expiresAt > 0 }
        }
    }
}
