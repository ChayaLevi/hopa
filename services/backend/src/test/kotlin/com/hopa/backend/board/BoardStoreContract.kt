package com.hopa.backend.board

import com.hopa.backend.auth.DeviceIdentity
import java.util.Collections
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * What every [BoardStore] must do, run against Postgres and in memory alike.
 * A subclass supplies the store and seeds households with real devices.
 */
abstract class BoardStoreContract {
    protected abstract val store: BoardStore

    /** A store over the same data, as after a reconnect or a server restart. */
    protected abstract fun reopened(): BoardStore

    /** A new household with [devices] devices, each of its own member. */
    protected abstract fun household(devices: Int): List<DeviceIdentity>

    private fun op(
        device: DeviceIdentity,
        scope: String = BoardScopes.FAMILY,
        opId: String = UUID.randomUUID().toString(),
        body: ByteArray = ByteArray(40) { 7 },
        keyEpoch: Int = 0,
    ) = NewBoardOp(opId, scope, device.deviceId, device.memberId, keyEpoch, body)

    private fun inParallel(threads: Int, tasks: List<() -> Unit>) {
        val start = CountDownLatch(1)
        val pool = Executors.newFixedThreadPool(threads)
        val futures = tasks.map { task ->
            pool.submit {
                start.await()
                task()
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(pool.awaitTermination(120, TimeUnit.SECONDS))
        futures.forEach { it.get() }
    }

    @Test
    fun `200 concurrent appends from 4 devices get seqs 1 to 200 with no gap or duplicate`() {
        val devices = household(4)
        val household = devices.first().householdId
        val seqs = Collections.synchronizedList(mutableListOf<Long>())

        inParallel(
            4,
            devices.mapIndexed { index, device ->
                {
                    repeat(50) { n ->
                        val scope = if ((index + n) % 3 == 0) BoardScopes.PARENTS else BoardScopes.FAMILY
                        val outcome = store.append(household, op(device, scope))
                        seqs += (outcome as AppendOutcome.Stored).seq
                    }
                }
            },
        )

        assertEquals((1L..200L).toList(), seqs.sorted())
        val stored = store.opsAfter(household, BoardScopes.FAMILY, 0, 500) +
            store.opsAfter(household, BoardScopes.PARENTS, 0, 500)
        assertEquals((1L..200L).toList(), stored.map { it.seq }.sorted())
    }

    @Test
    fun `each household counts its own seqs`() {
        val home = household(1).single()
        val other = household(1).single()

        store.append(home.householdId, op(home))
        store.append(home.householdId, op(home))

        assertEquals(AppendOutcome.Stored(1, duplicate = false), store.append(other.householdId, op(other)))
        assertEquals(AppendOutcome.Stored(3, duplicate = false), store.append(home.householdId, op(home)))
    }

    @Test
    fun `the same op id body and scope returns the stored seq as a duplicate and writes nothing`() {
        val device = household(1).single()
        val household = device.householdId
        val sent = op(device)
        store.append(household, op(device))

        val stored = store.append(household, sent)
        val again = store.append(household, op(device, opId = sent.opId, body = sent.body.copyOf()))

        assertEquals(AppendOutcome.Stored(2, duplicate = false), stored)
        assertEquals(AppendOutcome.Stored(2, duplicate = true), again)
        assertEquals(2, store.opsAfter(household, BoardScopes.FAMILY, 0, 500).size)
        // A duplicate burns no seq.
        assertEquals(AppendOutcome.Stored(3, duplicate = false), store.append(household, op(device)))
    }

    @Test
    fun `a replay after a reconnect is a duplicate`() {
        val device = household(1).single()
        val household = device.householdId
        val sent = op(device)
        store.append(household, sent)

        val replay = reopened().append(household, sent)

        assertEquals(AppendOutcome.Stored(1, duplicate = true), replay)
        assertEquals(1, reopened().opsAfter(household, BoardScopes.FAMILY, 0, 500).size)
    }

    @Test
    fun `the same op id with another body or scope is a conflict and burns no seq`() {
        val device = household(1).single()
        val household = device.householdId
        val sent = op(device)
        store.append(household, sent)

        val otherBody = store.append(household, op(device, opId = sent.opId, body = ByteArray(40) { 8 }))
        val otherScope = store.append(household, op(device, scope = BoardScopes.PARENTS, opId = sent.opId))

        assertEquals(AppendOutcome.Conflict, otherBody)
        assertEquals(AppendOutcome.Conflict, otherScope)
        assertEquals(1, store.opsAfter(household, BoardScopes.FAMILY, 0, 500).size)
        assertEquals(0, store.opsAfter(household, BoardScopes.PARENTS, 0, 500).size)
        assertEquals(AppendOutcome.Stored(2, duplicate = false), store.append(household, op(device)))
    }

    @Test
    fun `concurrent replays of one op store it once`() {
        val devices = household(4)
        val household = devices.first().householdId
        val sent = op(devices.first())
        val outcomes = Collections.synchronizedList(mutableListOf<AppendOutcome>())

        inParallel(4, List(8) { { outcomes += store.append(household, sent) } })

        assertEquals(8, outcomes.size)
        assertEquals(1, outcomes.count { it == AppendOutcome.Stored(1, duplicate = false) })
        assertEquals(7, outcomes.count { it == AppendOutcome.Stored(1, duplicate = true) })
        assertEquals(AppendOutcome.Stored(2, duplicate = false), store.append(household, op(devices[1])))
    }

    @Test
    fun `an op keeps its attested actor epoch and ciphertext`() {
        val device = household(1).single()
        val sent = op(device, scope = BoardScopes.PARENTS, keyEpoch = 3, body = ByteArray(64) { it.toByte() })
        store.append(device.householdId, sent)

        val read = store.opsAfter(device.householdId, BoardScopes.PARENTS, 0, 10).single()

        assertEquals(1L, read.seq)
        assertEquals(sent.opId, read.opId)
        assertEquals(device.deviceId, read.fromDevice)
        assertEquals(device.memberId, read.memberId)
        assertEquals(3, read.keyEpoch)
        assertTrue(sent.body.contentEquals(read.body))
    }

    @Test
    fun `ops after a seq come in order one scope at a time up to the limit`() {
        val device = household(1).single()
        val household = device.householdId
        repeat(10) { n ->
            store.append(household, op(device, if (n % 2 == 0) BoardScopes.FAMILY else BoardScopes.PARENTS))
        }

        val family = store.opsAfter(household, BoardScopes.FAMILY, 3, 2)

        assertEquals(listOf(5L, 7L), family.map { it.seq })
        assertEquals(mapOf(BoardScopes.FAMILY to 9L, BoardScopes.PARENTS to 10L), store.head(household))
        assertEquals(emptyMap<String, Long>(), store.head(household(1).single().householdId))
    }

    @Test
    fun `a cursor is upserted per device and scope`() {
        val (first, second) = household(2)
        val household = first.householdId

        store.putCursor(household, first.deviceId, BoardScopes.FAMILY, 4)
        store.putCursor(household, first.deviceId, BoardScopes.FAMILY, 9)
        store.putCursor(household, first.deviceId, BoardScopes.PARENTS, 2)
        store.putCursor(household, second.deviceId, BoardScopes.FAMILY, 1)

        assertEquals(
            mapOf(BoardScopes.FAMILY to 9L, BoardScopes.PARENTS to 2L),
            store.cursors(household, first.deviceId),
        )
        assertEquals(mapOf(BoardScopes.FAMILY to 1L), store.cursors(household, second.deviceId))
    }

    @Test
    fun `a device holds a scope only at the current epoch`() {
        val (parent, other, teen) = household(3)
        val household = parent.householdId
        assertNull(store.heldEpoch(household, parent.deviceId, BoardScopes.PARENTS))

        store.putWrap(household, BoardScopes.PARENTS, 0, parent.deviceId, ByteArray(60), null)
        store.putWrap(household, BoardScopes.PARENTS, 0, other.deviceId, ByteArray(60), parent.deviceId)
        assertEquals(0, store.heldEpoch(household, parent.deviceId, BoardScopes.PARENTS))
        assertEquals(0, store.heldEpoch(household, other.deviceId, BoardScopes.PARENTS))
        assertNull(store.heldEpoch(household, teen.deviceId, BoardScopes.PARENTS))

        store.putWrap(household, BoardScopes.PARENTS, 1, other.deviceId, ByteArray(60), null)
        assertNull(store.heldEpoch(household, parent.deviceId, BoardScopes.PARENTS))
        assertEquals(1, store.heldEpoch(household, other.deviceId, BoardScopes.PARENTS))
    }
}
