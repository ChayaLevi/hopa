package com.hopa.backend.board.client

import com.hopa.backend.crypto.KeyWrap
import com.hopa.backend.pairing.X25519Keys
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import javax.crypto.AEADBadTagException

class ScopeKeysTest {
    private class Device(val id: String) {
        private val pair = X25519Keys.generate()
        val publicKey: ByteArray = X25519Keys.publicKeyBytes(pair.public)
        val privateKey: ByteArray = pair.private.encoded
        val verified get() = VerifiedDevice(id, publicKey)
    }

    private val mom = Device("mom-phone")
    private val dad = Device("dad-laptop")
    private val server = Device("server-swapped")

    @Test
    fun `a parent wraps for a verified parent, who opens it`() {
        val momKeys = ScopeKeys(mom.privateKey, listOf(mom.verified, dad.verified))
        val key = momKeys.newKey()
        val wrapped = requireNotNull(momKeys.wrapFor("dad-laptop", key))
        val dadKeys = ScopeKeys(dad.privateKey, listOf(mom.verified))
        val opened = dadKeys.unwrap(wrapped, mom.publicKey) as Unwrapped.Key
        assertArrayEquals(key, opened.key)
    }

    @Test
    fun `no wrap for a key this device never verified (D16)`() {
        val momKeys = ScopeKeys(mom.privateKey, listOf(mom.verified))
        assertNull(momKeys.wrapFor("dad-laptop", momKeys.newKey()))
        assertNull(momKeys.wrapFor("server-swapped", momKeys.newKey()))
    }

    @Test
    fun `a wrap from an unverified key is refused with its fingerprint`() {
        // The server swaps in its own key and wraps a key it knows for dad.
        val serverKeys = ScopeKeys(server.privateKey, listOf(dad.verified))
        val wrapped = requireNotNull(serverKeys.wrapFor("dad-laptop", serverKeys.newKey()))
        val dadKeys = ScopeKeys(dad.privateKey, listOf(mom.verified))
        val refused = dadKeys.unwrap(wrapped, server.publicKey) as Unwrapped.Unverified
        assertEquals(KeyFingerprint.of(server.publicKey), refused.fingerprint)
        assertEquals(39, refused.fingerprint.length)
        assertTrue(refused.fingerprint.matches(Regex("([0-9a-f]{4} ){7}[0-9a-f]{4}")))
    }

    @Test
    fun `a scope-key wrap is not a household-key wrap`() {
        val key = KeyWrap.generateContentKey()
        val scopeWrap = requireNotNull(ScopeKeys(mom.privateKey, listOf(dad.verified)).wrapFor("dad-laptop", key))
        assertThrows(
            AEADBadTagException::class.java,
        ) { KeyWrap.unwrapByDevice(scopeWrap, mom.publicKey, dad.privateKey) }
        val householdWrap = KeyWrap.wrapForDevice(key, dad.publicKey, mom.privateKey)
        assertThrows(AEADBadTagException::class.java) {
            ScopeKeys(dad.privateKey, listOf(mom.verified)).unwrap(householdWrap, mom.publicKey)
        }
    }

    @Test
    fun `fingerprints are stable and differ per key`() {
        assertEquals(KeyFingerprint.of(mom.publicKey), KeyFingerprint.of(mom.publicKey.copyOf()))
        assertTrue(KeyFingerprint.of(mom.publicKey) != KeyFingerprint.of(dad.publicKey))
    }
}
