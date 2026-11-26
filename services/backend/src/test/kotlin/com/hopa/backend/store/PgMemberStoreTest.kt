package com.hopa.backend.store

import com.hopa.backend.board.BoardHouseholds
import com.hopa.backend.board.BoardHouseholds.MANAGER
import com.hopa.backend.board.BoardHouseholds.PARENT
import com.hopa.backend.board.BoardScopes
import com.hopa.backend.board.WrapRequest
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.members.PermissionChange
import com.hopa.backend.members.PermissionOutcome
import com.hopa.backend.members.Permissions
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/** [PgMemberStore] under concurrent writers (B5): the per-household lock holds the rules. */
class PgMemberStoreTest {
    private val db = PostgresHarness.appDatabase()
    private val members = PgMemberStore(db)
    private val board = PgBoardStore(db)

    /** Runs both blocks at once, released by one latch; returns their results in order. */
    private fun <A, B> together(first: () -> A, second: () -> B): Pair<A, B> {
        val pool = Executors.newFixedThreadPool(2)
        try {
            val start = CountDownLatch(1)
            val a = pool.submit<A> { start.await(); first() }
            val b = pool.submit<B> { start.await(); second() }
            start.countDown()
            return a.get(30, TimeUnit.SECONDS) to b.get(30, TimeUnit.SECONDS)
        } finally {
            pool.shutdownNow()
        }
    }

    @Test
    fun `two managers stepping down at once leave exactly one manager`() {
        repeat(10) {
            val (mom, dad) = BoardHouseholds.seed(listOf(MANAGER, MANAGER))
            val keep = setOf(Permissions.BOARD_PRIVATE)

            val (a, b) = together(
                { members.change(mom.householdId, PermissionChange(mom.memberId, mom.memberId, keep)) },
                { members.change(dad.householdId, PermissionChange(dad.memberId, dad.memberId, keep)) },
            )

            assertEquals(1, listOf(a, b).count { it is PermissionOutcome.Changed })
            val refused = listOf(a, b).filterIsInstance<PermissionOutcome.Refused>().single()
            assertEquals("E_CONFLICT", refused.error.code)
            val managers = members.list(mom.householdId).count { Permissions.GROUP_MANAGE in it.permissions }
            assertEquals(1, managers)
        }
    }

    @Test
    fun `a wrap racing the loss of board private never outlives it`() {
        repeat(10) {
            val (mom, dad) = BoardHouseholds.seed(listOf(MANAGER, PARENT))
            board.putWrap(mom.householdId, BoardScopes.PARENTS, 1, mom.deviceId, ByteArray(60), mom.deviceId)

            together(
                {
                    board.writeWrap(
                        mom.householdId,
                        WrapRequest(BoardScopes.PARENTS, mom.deviceId, dad.deviceId, 1, ByteArray(60)),
                    )
                },
                { members.change(mom.householdId, PermissionChange(mom.memberId, dad.memberId, emptySet())) },
            )

            assertEquals(null, board.heldEpoch(dad.householdId, dad.deviceId, BoardScopes.PARENTS))
        }
    }
}
