package com.hopa.family.scope

import com.hopa.family.board.BoardReducer
import com.hopa.family.board.BoardStep
import com.hopa.family.board.FixtureLogs
import com.hopa.family.model.BoardOp
import com.hopa.family.model.BoardState
import com.hopa.family.model.ItemStatus
import com.hopa.family.model.ItemType
import com.hopa.family.model.LogEntry
import com.hopa.family.model.NewItem
import com.hopa.family.model.Ownership
import com.hopa.family.model.OwnershipInput
import com.hopa.family.model.Scope
import com.hopa.family.model.Source
import com.hopa.family.model.SourceKind
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScopeHelpersTest {
    private val seed = FixtureLogs.seed
    private var counter = 0
    private val stamps = OpStamps {
        counter += 1
        OpStamp("op-test-$counter", "2026-11-11T13:%02d:00.000Z".format(counter))
    }

    @Test
    fun `a parent holds both scopes, a teen and a child only family`() {
        val roles = seed.family.members.associate { it.id to ScopeAccess.heldBy(it) }
        assertEquals(setOf(Scope.FAMILY, Scope.PARENTS), roles["mom"])
        assertEquals(setOf(Scope.FAMILY), roles["maya"])
        assertEquals(setOf(Scope.FAMILY), roles["noa"])
        val noa = seed.family.members.single { it.id == "noa" }
        assertFalse(ScopeAccess.mayWrite(noa, Scope.FAMILY), "no account, no writes")
        assertEquals(listOf("mom", "dad"), ScopeAccess.eligibleOwners(seed.family, Scope.PARENTS))
        assertEquals(listOf("mom", "dad", "maya"), ScopeAccess.eligibleOwners(seed.family, Scope.FAMILY))
    }

    @Test
    fun `a parent's view has both scopes and a teen's has no trace of parents`() {
        val mom = MemberViews.viewFor(seed, "mom")!!
        assertEquals(setOf(Scope.FAMILY, Scope.PARENTS), mom.scopes)
        assertTrue(mom.items.any { it.scope == Scope.PARENTS && it.item.id == "counselor-meeting" })

        val maya = MemberViews.viewFor(seed, "maya")!!
        assertEquals(setOf(Scope.FAMILY), maya.scopes)
        assertTrue(maya.items.all { it.scope == Scope.FAMILY })
        assertTrue(maya.items.none { it.item.id == "counselor-meeting" })

        assertNull(MemberViews.viewFor(seed, "grandma"))
    }

    @Test
    fun `a parent on a device without the parents key sees family only`() {
        val view = MemberViews.viewFor(seed.viewOf(setOf(Scope.FAMILY)), "dad")!!
        assertEquals(setOf(Scope.FAMILY), view.scopes)
    }

    @Test
    fun `the view drops undo patches and orders activity by time`() {
        val state = apply(seed, Scope.PARENTS, "dad", assign("counselor-meeting", 1, "dad"))
        val withFamily = apply(state, Scope.FAMILY, "mom", assign("pickup-today", 1, "dad"))
        val view = MemberViews.viewFor(withFamily, "mom")!!
        assertTrue(view.activity.isNotEmpty())
        assertTrue(view.activity.all { it.activity.undo == null })
        assertEquals(view.activity.sortedBy { it.activity.at }, view.activity)
        assertTrue(withFamily.scopes.parents!!.activity.last().undo != null, "the state keeps them")
    }

    @Test
    fun `an event moves with its open preparations, and every op applies`() {
        val plan = VisibilityMove.plan(seed, "dad", "family-dinner", Scope.FAMILY, Scope.PARENTS, { "$it-p" }, stamps)
        val ops = (plan as MovePlan.Ops).ops
        assertEquals(listOf(Scope.FAMILY, Scope.PARENTS, Scope.PARENTS), ops.map { it.scope })
        val prep = (ops[2].op as BoardOp.Create).item
        assertEquals("family-dinner-p", prep.parentEventId)
        assertEquals(OwnershipInput.Open(candidateIds = listOf("mom", "dad")), prep.ownership)

        val after = applyAll(seed, "dad", ops)
        assertTrue(
            after.scopes.family.items.filter {
                it.id.startsWith("family-dinner")
            }.all { it.status == ItemStatus.CANCELLED },
        )
        assertEquals(2, after.scopes.parents!!.items.count { it.id.endsWith("-p") })
    }

    @Test
    fun `moving a parents item to the family keeps its owner`() {
        val plan = VisibilityMove.plan(seed, "mom", "counselor-meeting", Scope.PARENTS, Scope.FAMILY, {
            "school-meeting"
        }, stamps)
        val ops = (plan as MovePlan.Ops).ops
        val after = applyAll(seed, "mom", ops)
        val moved = after.scopes.family.items.single { it.id == "school-meeting" }
        assertEquals(Ownership.Assigned("mom", confirmed = true), moved.ownership)
        assertTrue(moved.sensitive)
        assertNull(moved.titleZh)
    }

    @Test
    fun `an approved item from a group stays approved after the move`() {
        val group = Source(SourceKind.GROUP, "Kindergarten parents group")
        val created =
            apply(seed, Scope.FAMILY, "mom", create(NewItem("apples", ItemType.TASK, "Bring apples", source = group)))
        val approved = apply(created, Scope.FAMILY, "mom", BoardOp.Approve(1, "op-approve", AT, "apples", 1))
        val plan = VisibilityMove.plan(approved, "mom", "apples", Scope.FAMILY, Scope.PARENTS, { "apples-p" }, stamps)
        val ops = (plan as MovePlan.Ops).ops
        assertEquals(listOf("cancel", "create", "approve"), ops.map { kindOf(it.op) })
        val moved = applyAll(approved, "mom", ops).scopes.parents!!.items.single { it.id == "apples-p" }
        assertEquals(ItemStatus.OPEN, moved.status)
        assertEquals(group, moved.source)
    }

    @Test
    fun `a proposal moves as a proposal`() {
        val created = apply(seed, Scope.FAMILY, "mom", create(NewItem("idea", ItemType.TASK, "Idea", proposed = true)))
        val ops = (
            VisibilityMove.plan(created, "mom", "idea", Scope.FAMILY, Scope.PARENTS, {
                "idea-p"
            }, stamps) as MovePlan.Ops
            ).ops
        val moved = applyAll(created, "mom", ops).scopes.parents!!.items.single { it.id == "idea-p" }
        assertEquals(ItemStatus.PROPOSED, moved.status)
    }

    @Test
    fun `refusals`() {
        fun plan(state: BoardState, by: String, id: String, from: Scope, to: Scope) =
            (VisibilityMove.plan(state, by, id, from, to, { "$it-x" }, stamps) as MovePlan.Refused).reason

        assertEquals(MoveRefusal.NOT_ALLOWED, plan(seed, "maya", "pickup-today", Scope.FAMILY, Scope.PARENTS))
        assertEquals(MoveRefusal.NOT_ALLOWED, plan(seed, "mom", "pickup-today", Scope.FAMILY, Scope.FAMILY))
        assertEquals(
            MoveRefusal.NOT_ALLOWED,
            plan(seed.viewOf(setOf(Scope.FAMILY)), "mom", "pickup-today", Scope.FAMILY, Scope.PARENTS),
        )
        assertEquals(MoveRefusal.NOT_FOUND, plan(seed, "mom", "counselor-meeting", Scope.FAMILY, Scope.PARENTS))
        assertEquals(MoveRefusal.PREPARATION, plan(seed, "mom", "family-dinner-flowers", Scope.FAMILY, Scope.PARENTS))
        val done = apply(seed, Scope.FAMILY, "mom", BoardOp.Done(1, "op-done", AT, "swim-today", 1))
        assertEquals(MoveRefusal.CLOSED, plan(done, "mom", "swim-today", Scope.FAMILY, Scope.PARENTS))
    }

    @Test
    fun `a busy event is derived only from a parents event that makes someone unavailable`() {
        val stamp = OpStamp("op-busy", AT)
        assertNull(BusyDerivation.derive(seed, "mom", "counselor-meeting", "busy", stamp), "nobody unavailable")
        assertNull(BusyDerivation.derive(seed, "mom", "pickup-today", "busy", stamp), "not a parents event")
        val doctor = NewItem(
            "doctor",
            ItemType.EVENT,
            "Doctor",
            notes = "private",
            start = "2026-11-12T08:00:00.000Z",
            end = "2026-11-12T09:00:00.000Z",
            unavailableMemberIds = listOf("dad"),
        )
        val state = apply(seed, Scope.PARENTS, "dad", create(doctor))
        assertNull(BusyDerivation.derive(state, "maya", "doctor", "busy", stamp), "a teen holds no parents event")
        val derived = BusyDerivation.derive(state, "dad", "doctor", "busy", stamp)!!
        val item = (derived.op as BoardOp.Create).item
        assertEquals(Scope.FAMILY, derived.scope)
        assertEquals(BusyDerivation.DEFAULT_TITLE, item.title)
        assertNull(item.notes)
        assertEquals(listOf("dad"), item.unavailableMemberIds)
        val after = applyAll(state, "dad", listOf(derived))
        assertTrue(after.scopes.family.items.any { it.id == "busy" })
    }

    private fun kindOf(op: BoardOp): String = encode(op)["kind"].toString().trim('"')

    private fun encode(op: BoardOp): JsonObject =
        com.hopa.family.board.BoardJson.json.encodeToJsonElement(BoardOp.serializer(), op).jsonObject

    private fun create(item: NewItem) = BoardOp.Create(1, "op-create-${item.id}", AT, item)

    private fun assign(id: String, version: Int, owner: String) = BoardOp.Assign(
        1,
        "op-assign-$id",
        AT,
        id,
        version,
        owner,
    )

    private var seq = 0L

    private fun apply(state: BoardState, scope: Scope, by: String, op: BoardOp): BoardState {
        seq += 1
        val step = BoardReducer.apply(state, LogEntry(seq, scope, by, "$by-device", encode(op)))
        return (step as? BoardStep.Applied)?.state ?: error("seq $seq ${kindOf(op)}: $step")
    }

    private fun applyAll(state: BoardState, by: String, ops: List<ScopedOp>): BoardState =
        ops.fold(state) { acc, scoped -> apply(acc, scoped.scope, by, scoped.op) }

    private companion object {
        const val AT = "2026-11-11T12:45:00.000Z"
    }
}
