package com.hopa.backend.pairing

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.Test
import java.security.KeyFactory
import java.security.PublicKey
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Base64

class BackendPairingTest {
    @Test
    fun `claim sends the session and returns the tokens`() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """
                    {"deviceId":"d1","householdId":"h1","accessToken":"a1","refreshToken":"r1",
                     "expiresAt":"2026-11-17T00:00:00Z"}
                    """.trimIndent(),
                ),
            )
            start()
        }
        try {
            val pairing = BackendPairing(gateway(server))
            val credentials = pairing.claim(
                link(server),
                name = "Xiaomi 15T Pro",
                publicKey = "pk",
                pushToken = "tok",
            )

            assertEquals("d1", credentials.deviceId)
            assertEquals("h1", credentials.householdId)
            assertEquals("a1", credentials.accessToken)
            assertEquals("r1", credentials.refreshToken)
            assertEquals("2026-11-17T00:00:00Z", credentials.expiresAt)
            assertEquals(gateway(server), credentials.gatewayUrl)

            val request = server.takeRequest()
            assertEquals("/v1/pair/claim", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"session\":\"s1\""))
            assertTrue(body.contains("\"platform\":\"android\""))
            assertTrue(body.contains("\"name\":\"Xiaomi 15T Pro\""))
            assertTrue(body.contains("\"publicKey\":\"pk\""))
            assertTrue(body.contains("\"alg\":\"x25519\""))
            assertTrue(body.contains("\"pushToken\":\"tok\""))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `refresh posts the refresh token and keeps the gateway`() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"accessToken":"a2","refreshToken":"r2","expiresAt":"2026-11-17T00:15:00Z"}""",
                ),
            )
            start()
        }
        try {
            val credentials = BackendPairing(gateway(server)).refresh("r1")

            assertEquals("a2", credentials.accessToken)
            assertEquals("r2", credentials.refreshToken)
            assertEquals("2026-11-17T00:15:00Z", credentials.expiresAt)
            assertEquals(gateway(server), credentials.gatewayUrl)

            val request = server.takeRequest()
            assertEquals("/v1/token/refresh", request.path)
            assertEquals("""{"refreshToken":"r1"}""", request.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a refused claim throws with the server message`() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(400)
                    .setBody("""{"code":"E_SCHEMA","message":"expired"}"""),
            )
            start()
        }
        try {
            val pairing = BackendPairing(gateway(server))
            val message = messageOf {
                pairing.claim(link(server), name = "Xiaomi 15T Pro", publicKey = "pk", pushToken = null)
            }

            assertEquals("pairing refused: E_SCHEMA expired", message)
            assertFalse(message.contains("s1"))
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("\"session\":\"s1\""))
            assertFalse(body.contains("pushToken"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a refused refresh never prints the refresh token`() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(401)
                    .setBody("""{"code":"E_AUTH","message":"bad r1"}"""),
            )
            start()
        }
        try {
            val message = messageOf { BackendPairing(gateway(server)).refresh("r1") }

            assertEquals("pairing refused: E_AUTH bad <redacted>", message)
            assertFalse(message.contains("r1"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a malformed success body is refused without echoing it`() = runBlocking {
        val bodies = listOf(
            """[{"accessToken":"a1","refreshToken":"r1"}]""",
            "{\"accessToken\":\"a1\",\"refreshToken\":\"r1\"",
        )
        val server = MockWebServer().apply {
            bodies.forEach { enqueue(MockResponse().setResponseCode(201).setBody(it)) }
            start()
        }
        try {
            val pairing = BackendPairing(gateway(server))
            repeat(bodies.size) {
                val message = messageOf {
                    pairing.claim(link(server), name = "Xiaomi 15T Pro", publicKey = "pk", pushToken = null)
                }
                assertEquals("pairing returned no JSON object", message)
                assertFalse(message.contains("a1"))
                assertFalse(message.contains("r1"))
            }
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a success without tokens is refused on both paths`() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """{"deviceId":"d1","householdId":"h1","refreshToken":"r1",""" +
                        """"expiresAt":"2026-11-17T00:00:00Z"}""",
                ),
            )
            enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"refreshToken":"r2","expiresAt":"2026-11-17T00:15:00Z"}""",
                ),
            )
            start()
        }
        try {
            val pairing = BackendPairing(gateway(server))
            val claimMessage = messageOf {
                pairing.claim(link(server), name = "Xiaomi 15T Pro", publicKey = "pk", pushToken = null)
            }
            val refreshMessage = messageOf { pairing.refresh("r1") }

            assertEquals("pairing response is incomplete: missing tokens", claimMessage)
            assertEquals("pairing response is incomplete: missing tokens", refreshMessage)
            assertFalse(claimMessage.contains("r1"))
            assertFalse(refreshMessage.contains("r2"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a claim without a device identity is refused`() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """{"accessToken":"a1","refreshToken":"r1","expiresAt":"2026-11-17T00:00:00Z"}""",
                ),
            )
            start()
        }
        try {
            val message = messageOf {
                BackendPairing(gateway(server))
                    .claim(link(server), name = "Xiaomi 15T Pro", publicKey = "pk", pushToken = null)
            }

            assertEquals("pairing response is incomplete: missing device identity", message)
            assertFalse(message.contains("a1"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `mintPending posts a windows x25519 session and returns it`() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """{"sessionId":"sid","secret":"s1","qr":"hopa://pair?v=1&s=s1&u=wss://a/v1/gateway",""" +
                        """"expiresAt":"2026-11-17T00:05:00Z"}""",
                ),
            )
            start()
        }
        try {
            val pending = BackendPairing(gateway(server)).mintPending("Studio", "pk")

            assertEquals("sid", pending.sessionId)
            assertEquals("s1", pending.secret)
            assertEquals("hopa://pair?v=1&s=s1&u=wss://a/v1/gateway", pending.qr)
            assertEquals("2026-11-17T00:05:00Z", pending.expiresAt)

            val request = server.takeRequest()
            assertEquals("/v1/pair/pending", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"platform\":\"windows\""))
            assertTrue(body.contains("\"name\":\"Studio\""))
            assertTrue(body.contains("\"publicKey\":\"pk\""))
            assertTrue(body.contains("\"alg\":\"x25519\""))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `pollPending resolves by the secret and returns the state`() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(200).setBody(
                    """{"sessionId":"sid","state":"vouched","name":"Studio","platform":"windows",""" +
                        """"publicKey":"pk","fingerprint":"abcd1234","expiresAt":"2026-11-17T00:05:00Z"}""",
                ),
            )
            start()
        }
        try {
            val state = BackendPairing(gateway(server)).pollPending("s1")

            assertEquals("vouched", state.state)
            assertEquals("Studio", state.name)
            assertEquals("pk", state.publicKey)
            assertEquals("abcd1234", state.fingerprint)
            assertEquals("2026-11-17T00:05:00Z", state.expiresAt)

            val request = server.takeRequest()
            assertEquals("/v1/pair/pending/resolve", request.path)
            assertEquals("""{"sessionSecret":"s1"}""", request.body.readUtf8())
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `claimPending returns the tokens and the wrapped and voucher keys`() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """{"deviceId":"d1","householdId":"h1","accessToken":"a1","refreshToken":"r1",""" +
                        """"expiresAt":"2026-11-17T00:05:00Z","wrappedKey":"d3JhcA",""" +
                        """"voucherPublicKey":"cGhvbmU"}""",
                ),
            )
            start()
        }
        try {
            val claimed = BackendPairing(gateway(server)).claimPending("s1", "Studio", "pk")

            assertEquals("d1", claimed.credentials.deviceId)
            assertEquals("a1", claimed.credentials.accessToken)
            assertEquals("d3JhcA", claimed.wrappedKey)
            assertEquals("cGhvbmU", claimed.voucherPublicKey)

            val request = server.takeRequest()
            assertEquals("/v1/pair/claim", request.path)
            val body = request.body.readUtf8()
            assertTrue(body.contains("\"session\":\"s1\""))
            assertTrue(body.contains("\"platform\":\"windows\""))
            assertTrue(body.contains("\"alg\":\"x25519\""))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a refused poll never prints the secret`() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(400)
                    .setBody("""{"code":"E_SCHEMA","message":"unknown s1"}"""),
            )
            start()
        }
        try {
            val message = messageOf { BackendPairing(gateway(server)).pollPending("s1") }

            assertEquals("pairing refused: E_SCHEMA unknown <redacted>", message)
            assertFalse(message.contains("s1"))
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `a 429 is a retryable failure`() = runBlocking {
        val server = MockWebServer().apply {
            enqueue(
                MockResponse().setResponseCode(429)
                    .setBody("""{"code":"E_RATE","message":"too many pending pairing polls","retryable":true}"""),
            )
            start()
        }
        try {
            val failure = failureOf { BackendPairing(gateway(server)).pollPending("s1") }

            assertTrue(failure is BackendPairingFailure.Unavailable)
            assertEquals("pairing refused: E_RATE too many pending pairing polls", failure.message)
        } finally {
            server.shutdown()
        }
    }

    @Test
    fun `an x25519 public key is thirty two bytes and a private key round-trips`() {
        val pair = X25519Keys.generate()
        assertEquals(32, X25519Keys.publicKeyBytes(pair.public).size)

        val encoded = X25519Keys.privateKeyBase64(pair.`private`)
        assertTrue(encoded.isNotBlank())
        val rebuilt = KeyFactory.getInstance("X25519")
            .generatePrivate(PKCS8EncodedKeySpec(Base64.getDecoder().decode(encoded)))
        assertEquals(pair.`private`.encoded.toList(), rebuilt.encoded.toList())
    }

    @Test
    fun `an android provider key is read from its x509 encoding`() {
        val raw = ByteArray(32) { it.toByte() }
        val key = FakePublicKey(X25519_SPKI_PREFIX + raw)

        assertEquals(raw.toList(), X25519Keys.publicKeyBytes(key).toList())
    }

    @Test
    fun `a key that is not x25519 is rejected`() {
        try {
            X25519Keys.publicKeyBytes(FakePublicKey(ByteArray(X25519_SPKI_PREFIX.size + 32)))
            fail("expected IllegalArgumentException")
        } catch (error: IllegalArgumentException) {
            assertEquals("not an X25519 public key", error.message)
        }
    }

    /** Mimics an Android provider key, which is not an [java.security.interfaces.XECPublicKey]. */
    private class FakePublicKey(private val encoded: ByteArray) : PublicKey {
        override fun getAlgorithm(): String = "X25519"

        override fun getFormat(): String = "X.509"

        override fun getEncoded(): ByteArray = encoded
    }

    private suspend fun messageOf(block: suspend () -> Unit): String = try {
        block()
        "no error"
    } catch (error: IllegalStateException) {
        error.message.orEmpty()
    }

    private suspend fun failureOf(block: suspend () -> Unit): BackendPairingFailure = try {
        block()
        fail("expected a pairing failure")
    } catch (error: BackendPairingFailure) {
        error
    }

    private fun link(server: MockWebServer) = PairingLink(
        householdId = "h1",
        session = "s1",
        gatewayUrl = gateway(server),
    )

    /** MockWebServer speaks plain HTTP, so the socket scheme is `ws`; the
     *  `wss` to `https` mapping is pinned by PairingLinksTest. */
    private fun gateway(server: MockWebServer) = "ws://${server.hostName}:${server.port}/v1/gateway"

    private companion object {
        /** X.509 SubjectPublicKeyInfo header for X25519, OID 1.3.101.110. */
        val X25519_SPKI_PREFIX = byteArrayOf(
            0x30, 0x2a, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x6e, 0x03, 0x21, 0x00,
        )
    }
}
