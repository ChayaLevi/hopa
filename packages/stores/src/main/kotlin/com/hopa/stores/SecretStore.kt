package com.hopa.stores

interface SecretStore {
    suspend fun get(alias: String): String

    suspend fun set(alias: String, value: String)

    suspend fun clear(alias: String)

    /** True when a value is stored, even if it cannot be decrypted right now. */
    suspend fun present(alias: String): Boolean
}
