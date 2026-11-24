package com.hopa.backend.pairing

/**
 * The answer to a phone + password sign-up or sign-in: the account and the
 * freshly admitted device's tokens. Tokens are bearer capabilities and are
 * redacted from [toString].
 */
data class SignedInAccount(
    val accountId: String,
    val deviceId: String,
    val householdId: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: String,
) {
    override fun toString(): String = "SignedInAccount(accountId=$accountId, deviceId=$deviceId, " +
        "householdId=$householdId, accessToken=<redacted>, refreshToken=<redacted>, " +
        "expiresAt=$expiresAt)"
}
