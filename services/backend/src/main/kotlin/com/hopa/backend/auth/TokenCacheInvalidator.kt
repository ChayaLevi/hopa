package com.hopa.backend.auth

/**
 * Drops any cached resolution of a device's tokens after a revoke. Kept
 * separate from [AuthPort] so a decorated auth port can still be asked, and a
 * cache can never silently fall back to its TTL.
 */
interface TokenCacheInvalidator {
    fun invalidate(deviceId: String)
}
