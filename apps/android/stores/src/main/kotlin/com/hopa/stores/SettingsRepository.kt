package com.hopa.stores

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun update(transform: (AppSettings) -> AppSettings)

    suspend fun modelKey(): String

    /** True when a model key is stored, even if it cannot be decrypted. */
    suspend fun hasModelKey(): Boolean

    suspend fun setModelKey(value: String)

    suspend fun bridgeToken(): String

    suspend fun setBridgeToken(value: String)

    suspend fun bridgeRefresh(): String

    suspend fun setBridgeRefresh(value: String)

    suspend fun backendAccessToken(): String

    suspend fun setBackendAccessToken(value: String)

    suspend fun backendRefreshToken(): String

    suspend fun setBackendRefreshToken(value: String)

    suspend fun backendPrivateKey(): String

    suspend fun setBackendPrivateKey(value: String)

    /**
     * The household content key this phone generated at sign-up. It lives in
     * the secret store next to the other backend secrets, never in [AppSettings]
     * or the backend credentials.
     */
    suspend fun backendContentKey(): String

    suspend fun setBackendContentKey(value: String)

    /**
     * The Ed25519 seed that signs this phone's approval answers (ADR-0028),
     * base64. Blank until the first answer creates it. A store that cannot
     * keep it returns blank: each answer then signs with a new key, which the
     * asking PC refuses once it pinned one, so forgetting fails closed.
     */
    suspend fun approvalSigningKey(): String = ""

    suspend fun setApprovalSigningKey(value: String) = Unit

    /** Forget the gateway pairing: tokens and key, plus the identity fields. */
    suspend fun clearBackend()
}
