package com.hopa.backend.approval

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import java.util.Base64

/**
 * `body.approval.signature` in a `task.result` (ADR-0032): the answering
 * device's Ed25519 public [key] and its [sig] over [SignedAnswer.message],
 * both base64url without padding.
 */
data class AnswerSignature(
    val key: ByteArray,
    val sig: ByteArray,
) {
    fun toJson(): JsonObject = buildJsonObject {
        put("v", VERSION)
        put("alg", ALG)
        put("key", encode(key))
        put("sig", encode(sig))
    }

    override fun equals(other: Any?): Boolean =
        other is AnswerSignature && key.contentEquals(other.key) && sig.contentEquals(other.sig)

    override fun hashCode(): Int = 31 * key.contentHashCode() + sig.contentHashCode()

    companion object {
        const val VERSION = 1
        const val ALG = "Ed25519"

        /** The signature in an answer, or null when it is missing or malformed. */
        fun parse(element: JsonElement?): AnswerSignature? {
            val obj = element as? JsonObject ?: return null
            if ((obj["v"] as? JsonPrimitive)?.longOrNull != VERSION.toLong()) return null
            if (text(obj, "alg") != ALG) return null
            val key = text(obj, "key")?.let(::decode) ?: return null
            val sig = text(obj, "sig")?.let(::decode) ?: return null
            if (key.size != AnswerSigning.KEY_BYTES || sig.size != AnswerSigning.SIGNATURE_BYTES) return null
            return AnswerSignature(key, sig)
        }

        fun encode(bytes: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

        fun decode(text: String): ByteArray? = runCatching { Base64.getUrlDecoder().decode(text) }.getOrNull()

        private fun text(obj: JsonObject, key: String): String? =
            (obj[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
    }
}
