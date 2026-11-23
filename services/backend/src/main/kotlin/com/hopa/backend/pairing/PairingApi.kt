package com.hopa.backend.pairing

import kotlinx.serialization.Serializable

sealed interface PairingOutcome<out T> {
    data class Ok<T>(val value: T) : PairingOutcome<T>

    data class Refused(
        val code: String,
        val message: String,
        val retryable: Boolean = false,
    ) : PairingOutcome<Nothing>
}

@Serializable
data class MintRequest(
    val householdName: String,
    val memberName: String,
    val householdId: String? = null,
)

@Serializable
data class Minted(
    val session: String,
    val qr: String,
    val expiresAt: String,
)

@Serializable
data class ClaimRequest(
    val session: String,
    val platform: String,
    val name: String,
    val publicKey: String,
    val alg: String,
    val pushToken: String? = null,
)

@Serializable
data class Claimed(
    val deviceId: String,
    val householdId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String,
    /**
     * Base64url of the household key wrap this device was admitted with, when it
     * came through a vouched pending session. Null (and omitted from the JSON)
     * for an operator-minted session, which carries no wrap.
     */
    val wrappedKey: String? = null,
    /**
     * Base64url of the vouching phone's 32-byte X25519 public key. Present with
     * `wrappedKey`; the PC needs it to derive the same static-static KEK. Null
     * (and omitted) for an operator-minted session.
     */
    val voucherPublicKey: String? = null,
)

interface PairingApi {
    fun mint(
        householdName: String,
        memberName: String,
        remoteAddress: String,
        householdId: String? = null,
    ): PairingOutcome<Minted>

    fun claim(request: ClaimRequest, remoteAddress: String): PairingOutcome<Claimed>
}
