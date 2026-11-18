package com.hopa.app.gateway

import com.hopa.app.gateway.SessionFixtures.FakeLink
import com.hopa.app.gateway.SessionFixtures.FakeSettings
import com.hopa.app.gateway.SessionFixtures.session
import com.hopa.transport.gateway.BackendState
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** A new FCM token must never close the socket that an in-flight run is using. */
class BackendSessionTestReconnect {
    @Test
    fun `a token refresh in the background creates no link`() = runBlocking {
        val settings = FakeSettings().paired("wss://x/v1/gateway")
        val builds = AtomicInteger()
        val session = session(settings, linkFactory = { credentials, _, _, pushToken, refresh ->
            builds.incrementAndGet()
            FakeLink(credentials, pushToken, refresh)
        })
        try {
            FirebaseToken.set("t1")
            session.onBackground()
            session.reconnect()
            delay(150)

            assertEquals(0, builds.get())
            assertTrue(session.state.value is BackendState.Disconnected)
            assertEquals("d1", settings.snapshot.backendDeviceId)
            assertEquals("h1", settings.snapshot.backendHouseholdId)
            assertEquals("wss://x/v1/gateway", settings.snapshot.backendUrl)
            assertEquals("a1", settings.backendAccessToken())
            assertEquals("r1", settings.backendRefreshToken())
            assertEquals("key", settings.backendPrivateKey())
        } finally {
            FirebaseToken.set(null)
            session.disconnect()
        }
    }

    @Test
    fun `a token refresh during a background run does not stop the link`() = runBlocking {
        val settings = FakeSettings().paired("wss://x/v1/gateway")
        val created = Collections.synchronizedList(mutableListOf<FakeLink>())
        val session = session(settings, linkFactory = { credentials, _, _, pushToken, refresh ->
            FakeLink(credentials, pushToken, refresh).also { created += it }
        })
        try {
            FirebaseToken.set("t1")
            session.onForeground()
            withTimeout(2_000) { while (created.size < 1 || !created[0].started) delay(20) }
            session.taskStarted()
            session.onBackground()

            FirebaseToken.set("t2")
            session.reconnect()
            delay(150)

            assertFalse(created[0].stopped)
            assertEquals(1, created.size)
            assertTrue(session.state.value is BackendState.Live)

            session.taskFinished()
            delay(150)

            // The background rules keep the run's socket; no connect is forced.
            assertFalse(created[0].stopped)
            assertEquals(1, created.size)
            assertTrue(session.state.value is BackendState.Live)

            // The next link built after the stop carries the new token.
            session.disconnect()
            assertTrue(created[0].stopped)
            session.onForeground()
            withTimeout(2_000) { while (created.size < 2 || !created[1].started) delay(20) }
            assertEquals("t2", created[1].pushToken())
        } finally {
            FirebaseToken.set(null)
            session.disconnect()
        }
    }

    @Test
    fun `a token refresh during a foreground run waits for the run`() = runBlocking {
        val settings = FakeSettings().paired("wss://x/v1/gateway")
        val created = Collections.synchronizedList(mutableListOf<FakeLink>())
        val session = session(settings, linkFactory = { credentials, _, _, pushToken, refresh ->
            FakeLink(credentials, pushToken, refresh).also { created += it }
        })
        try {
            FirebaseToken.set("t1")
            session.onForeground()
            withTimeout(2_000) { while (created.size < 1 || !created[0].started) delay(20) }
            session.taskStarted()

            FirebaseToken.set("t2")
            session.reconnect()
            delay(150)
            assertFalse(created[0].stopped)
            assertEquals(1, created.size)

            session.taskFinished()
            withTimeout(2_000) { while (created.size < 2 || !created[1].started) delay(20) }
            withTimeout(2_000) {
                while (session.state.value !is BackendState.Live) delay(20)
            }

            assertTrue(created[0].stopped)
            assertEquals("t2", created[1].pushToken())
        } finally {
            FirebaseToken.set(null)
            session.disconnect()
        }
    }

    @Test
    fun `a token refresh in the foreground reopens the live link`() = runBlocking {
        val settings = FakeSettings().paired("wss://x/v1/gateway")
        val created = Collections.synchronizedList(mutableListOf<FakeLink>())
        val session = session(settings, linkFactory = { credentials, _, _, pushToken, refresh ->
            FakeLink(credentials, pushToken, refresh).also { created += it }
        })
        try {
            FirebaseToken.set("t1")
            session.onForeground()
            withTimeout(2_000) { while (created.size < 1 || !created[0].started) delay(20) }

            FirebaseToken.set("t2")
            session.reconnect()
            withTimeout(2_000) { while (created.size < 2 || !created[1].started) delay(20) }

            assertTrue(created[0].stopped)
            assertEquals("t2", created[1].pushToken())
        } finally {
            FirebaseToken.set(null)
            session.disconnect()
        }
    }
}
