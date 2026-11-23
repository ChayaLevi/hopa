package com.hopa.backend.gateway

import com.hopa.backend.auth.DeviceIdentity
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.channels.Channel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceRegistryTest {
    private val identity = DeviceIdentity("dev-a", "hh-1", "mem-1")

    private fun session(): DeviceRegistry.Session =
        DeviceRegistry.Session(identity, Channel())

    @Test
    fun `a stale session cannot remove the session that replaced it`() {
        val registry = DeviceRegistry()
        val first = session()
        val second = session()
        registry.join(first)
        registry.join(second)

        registry.leave(first)

        assertTrue(registry.online("dev-a"), "the reconnect must still be online")
        assertEquals(second, registry.sessionFor("dev-a"))
    }

    @Test
    fun `leaving the current session removes it`() {
        val registry = DeviceRegistry()
        val only = session()
        registry.join(only)

        registry.leave(only)

        assertTrue(!registry.online("dev-a"))
    }

    @Test
    fun `a kick before attach closes the new session and it never comes online`() {
        val registry = DeviceRegistry()

        // The revoke commits after deviceFor resolved but before attach runs.
        registry.kick("dev-a")
        val late = session()

        assertFalse(registry.attach(late), "a tombstoned socket must not attach")
        assertTrue(late.closed.isCompleted, "the close signal must already be complete")
        assertFalse(registry.connected("dev-a"))

        assertFalse(registry.join(late), "a tombstoned device must never join presence")
        assertFalse(registry.online("dev-a"))
    }

    @Test
    fun `the kick tombstone is pruned after five minutes`() {
        val now = AtomicLong(0)
        val registry = DeviceRegistry(clock = now::get)
        registry.kick("dev-a")

        now.set(5 * 60 * 1000 + 1)
        val fresh = session()

        assertTrue(registry.attach(fresh), "the tombstone must have expired")
        assertFalse(fresh.closed.isCompleted)
        assertTrue(registry.connected("dev-a"))
    }

    @Test
    fun `a kick racing an attach and join always closes the session`() {
        val pool = Executors.newFixedThreadPool(2)
        try {
            repeat(200) {
                val registry = DeviceRegistry()
                val late = session()
                val barrier = CyclicBarrier(2)
                val kick = pool.submit {
                    barrier.await()
                    registry.kick("dev-a")
                }
                val attachAndJoin = pool.submit {
                    barrier.await()
                    registry.attach(late)
                    registry.join(late)
                }
                kick.get(5, TimeUnit.SECONDS)
                attachAndJoin.get(5, TimeUnit.SECONDS)

                assertTrue(
                    late.closed.isCompleted,
                    "kick must close the session whether it wins before or after attach",
                )
            }
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `detach still finds a session after its sent set is mutated`() {
        val registry = DeviceRegistry()
        val only = session()
        registry.attach(only)

        // TaskRouter records task ids on the live session; identity equality
        // means the map key does not move.
        only.sent.add("task-1")
        registry.detach(only)

        assertFalse(registry.connected("dev-a"), "a mutated session must not leak")
    }
}
