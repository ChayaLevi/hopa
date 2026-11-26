package com.hopa.backend.board

import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.store.PgBoardStore
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The scope rule, the epoch rule, the shape checks and paging, on Postgres.
 * Households are seeded with family roles through the harness: two parents, a
 * teen and a child. Only the parents' devices hold the parents key.
 */
class BoardServiceTest {
    private val store = PgBoardStore(PostgresHarness.appDatabase())
    private val service = BoardService(store)

    private class Family(
        val mom: DeviceIdentity,
        val dad: DeviceIdentity,
        val teen: DeviceIdentity,
        val child: DeviceIdentity,
    )

    private fun family(): Family {
        val (mom, dad, teen, child) = BoardHouseholds.seed(listOf("parent", "parent", "teen", "child"))
        store.putWrap(mom.householdId, BoardScopes.PARENTS, 0, mom.deviceId, ByteArray(60) { 1 }, null)
        store.putWrap(mom.householdId, BoardScopes.PARENTS, 0, dad.deviceId, ByteArray(60) { 2 }, mom.deviceId)
        return Family(mom, dad, teen, child)
    }

    private fun ct(size: Int = 64) = ByteArray(size) { 3 }

    private fun newOpId() = UUID.randomUUID().toString()

    private fun <T> ok(result: BoardResult<T>): T = (result as BoardResult.Ok<T>).value

    private fun code(result: BoardResult<*>): String = (result as BoardResult.Failed).error.code

    @Test
    fun `a parent appends to both scopes and gets one seq order across them`() {
        val home = family()

        val family = ok(service.append(home.mom, newOpId(), BoardScopes.FAMILY, 0, ct()))
        val parents = ok(service.append(home.dad, newOpId(), BoardScopes.PARENTS, 0, ct()))

        assertEquals(BoardAck(BoardScopes.FAMILY, 1, duplicate = false), family)
        assertEquals(BoardAck(BoardScopes.PARENTS, 2, duplicate = false), parents)
    }

    @Test
    fun `a teen device cannot append to parents and gets nothing from it`() {
        val home = family()
        service.append(home.mom, newOpId(), BoardScopes.PARENTS, 0, ct())

        val append = service.append(home.teen, newOpId(), BoardScopes.PARENTS, 0, ct())
        val read = service.opsAfter(home.teen, BoardScopes.PARENTS, 0)
        val cursor = service.putCursor(home.teen, BoardScopes.PARENTS, 1)
        val head = service.head(home.teen)

        assertEquals("E_SCOPE", code(append))
        assertEquals("E_SCOPE", code(read))
        assertEquals("E_SCOPE", code(cursor))
        assertEquals(mapOf(BoardScopes.FAMILY to 0L), head)
        assertFalse(service.mayRead(home.child, BoardScopes.PARENTS))
        // The refused append wrote nothing.
        assertEquals(listOf(1L), ok(service.opsAfter(home.mom, BoardScopes.PARENTS, 0)).ops.map { it.seq })
    }

    @Test
    fun `every device of the household reads family and parents see both heads`() {
        val home = family()
        service.append(home.teen, newOpId(), BoardScopes.FAMILY, 0, ct())
        service.append(home.mom, newOpId(), BoardScopes.PARENTS, 0, ct())

        val childReads = ok(service.opsAfter(home.child, BoardScopes.FAMILY, 0)).ops

        assertEquals(listOf(1L), childReads.map { it.seq })
        assertEquals(home.teen.deviceId, childReads.single().fromDevice)
        assertEquals(home.teen.memberId, childReads.single().memberId)
        assertEquals(mapOf(BoardScopes.FAMILY to 1L, BoardScopes.PARENTS to 2L), service.head(home.dad))
    }

    @Test
    fun `a device of another household sees none of this board`() {
        val home = family()
        val stranger = family()
        service.append(home.mom, newOpId(), BoardScopes.FAMILY, 0, ct())
        service.append(home.mom, newOpId(), BoardScopes.PARENTS, 0, ct())

        assertTrue(ok(service.opsAfter(stranger.mom, BoardScopes.FAMILY, 0)).ops.isEmpty())
        assertTrue(ok(service.opsAfter(stranger.mom, BoardScopes.PARENTS, 0)).ops.isEmpty())
        assertEquals(mapOf(BoardScopes.FAMILY to 0L, BoardScopes.PARENTS to 0L), service.head(stranger.mom))
    }

    @Test
    fun `paging returns every op of a scope exactly once`() {
        val home = family()
        // A small page keeps the test fast; the cap logic is the same as for 500.
        val paging = BoardService(store, maxPage = 50)
        val sent = mutableListOf<Long>()
        repeat(123) { n ->
            val scope = if (n % 4 == 0) BoardScopes.PARENTS else BoardScopes.FAMILY
            val ack = ok(paging.append(home.mom, newOpId(), scope, 0, ct()))
            if (scope == BoardScopes.FAMILY) sent += ack.seq
        }

        val seen = mutableListOf<Long>()
        var after = 0L
        var pages = 0
        do {
            val page = ok(paging.opsAfter(home.teen, BoardScopes.FAMILY, after, limit = 1_000))
            assertTrue(page.ops.size <= 50)
            seen += page.ops.map { it.seq }
            after = page.ops.lastOrNull()?.seq ?: after
            pages++
        } while (page.hasMore)

        assertEquals(sent, seen)
        assertEquals(92, seen.size)
        assertEquals(2, pages)
        val small = ok(paging.opsAfter(home.teen, BoardScopes.FAMILY, 0, limit = 3))
        assertEquals(sent.take(3), small.ops.map { it.seq })
        assertTrue(small.hasMore)
        assertEquals(500, BoardService.MAX_PAGE)
    }

    @Test
    fun `a duplicate is acked with its stored seq and a conflict is E_CONFLICT`() {
        val home = family()
        val opId = newOpId()
        val first = ok(service.append(home.teen, opId, BoardScopes.FAMILY, 0, ct()))

        val replay = ok(service.append(home.teen, opId, BoardScopes.FAMILY, 0, ct()))
        val conflict = service.append(home.teen, opId, BoardScopes.FAMILY, 0, ct(65))

        assertEquals(BoardAck(BoardScopes.FAMILY, first.seq, duplicate = false), first)
        assertEquals(BoardAck(BoardScopes.FAMILY, first.seq, duplicate = true), replay)
        assertEquals("E_CONFLICT", code(conflict))
    }

    @Test
    fun `parents ops carry the current epoch and a device left on an old epoch is refused`() {
        val home = family()
        assertEquals("E_SCOPE", code(service.append(home.mom, newOpId(), BoardScopes.PARENTS, 1, ct())))

        store.putWrap(home.mom.householdId, BoardScopes.PARENTS, 1, home.mom.deviceId, ByteArray(60), null)

        val current = service.append(home.mom, newOpId(), BoardScopes.PARENTS, 1, ct())
        assertEquals(BoardAck(BoardScopes.PARENTS, 1, duplicate = false), ok(current))
        assertEquals("E_SCOPE", code(service.append(home.mom, newOpId(), BoardScopes.PARENTS, 0, ct())))
        assertEquals("E_SCOPE", code(service.append(home.dad, newOpId(), BoardScopes.PARENTS, 0, ct())))
        assertEquals("E_SCOPE", code(service.opsAfter(home.dad, BoardScopes.PARENTS, 0)))
    }

    @Test
    fun `malformed appends are E_SCHEMA and write nothing`() {
        val home = family()

        val refused = listOf(
            service.append(home.mom, newOpId(), "friends", 0, ct()),
            service.append(home.mom, "not-a-uuid", BoardScopes.FAMILY, 0, ct()),
            service.append(home.mom, newOpId(), BoardScopes.FAMILY, 0, ct(BoardService.MIN_CIPHERTEXT_BYTES - 1)),
            service.append(home.mom, newOpId(), BoardScopes.FAMILY, 0, ct(BoardService.MAX_CIPHERTEXT_BYTES + 1)),
            service.append(home.mom, newOpId(), BoardScopes.FAMILY, 2, ct()),
            service.opsAfter(home.mom, "friends", 0),
            service.opsAfter(home.mom, BoardScopes.FAMILY, -1),
            service.opsAfter(home.mom, BoardScopes.FAMILY, 0, limit = 0),
            service.putCursor(home.mom, BoardScopes.FAMILY, -1),
        )

        assertEquals(List(refused.size) { "E_SCHEMA" }, refused.map { code(it) })
        assertEquals(mapOf(BoardScopes.FAMILY to 0L, BoardScopes.PARENTS to 0L), service.head(home.mom))
        val largest = service.append(home.mom, newOpId(), BoardScopes.FAMILY, 0, ct(BoardService.MAX_CIPHERTEXT_BYTES))
        assertEquals(1L, ok(largest).seq)
    }

    @Test
    fun `a cursor records what the device applied`() {
        val home = family()

        ok(service.putCursor(home.teen, BoardScopes.FAMILY, 12))
        ok(service.putCursor(home.dad, BoardScopes.PARENTS, 4))

        assertEquals(mapOf(BoardScopes.FAMILY to 12L), store.cursors(home.teen.householdId, home.teen.deviceId))
        assertEquals(mapOf(BoardScopes.PARENTS to 4L), store.cursors(home.dad.householdId, home.dad.deviceId))
    }
}
