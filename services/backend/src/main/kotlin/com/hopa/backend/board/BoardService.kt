package com.hopa.backend.board

import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.protocol.GatewayError
import java.util.UUID

/**
 * The board's rules on top of [BoardStore] (ADR-0031).
 *
 * The scope rule lives here, not in RLS: a device reads or writes `parents`
 * only while it holds a `scope_key_wraps` row for `parents` at the current
 * epoch; otherwise the call is `E_SCOPE`. `family` is open to every device of
 * the household. The caller's household, device and member always come from
 * [DeviceIdentity], never from the request.
 *
 * Key epochs: the household key has no epochs in v1, so a `family` op carries
 * epoch 0; a `parents` op carries the current parents epoch, the one the
 * device holds.
 */
class BoardService(
    private val store: BoardStore,
    private val maxCiphertextBytes: Int = MAX_CIPHERTEXT_BYTES,
    private val maxPage: Int = MAX_PAGE,
) {
    fun append(
        caller: DeviceIdentity,
        opId: String,
        scope: String,
        keyEpoch: Int,
        ciphertext: ByteArray,
    ): BoardResult<BoardAck> {
        unknownScope(scope)?.let { return it }
        if (!isUuid(opId)) return schema("op id must be a UUID")
        if (ciphertext.size < MIN_CIPHERTEXT_BYTES) return schema("ciphertext is shorter than IV and tag")
        if (ciphertext.size > maxCiphertextBytes) return schema("ciphertext is larger than $maxCiphertextBytes bytes")
        val expectedEpoch = when (scope) {
            BoardScopes.FAMILY -> FAMILY_EPOCH
            else -> store.heldEpoch(caller.householdId, caller.deviceId, scope) ?: return refused(scope)
        }
        if (keyEpoch != expectedEpoch) {
            return if (scope == BoardScopes.FAMILY) schema("family ops use key epoch $FAMILY_EPOCH") else refused(scope)
        }
        val op = NewBoardOp(opId, scope, caller.deviceId, caller.memberId, keyEpoch, ciphertext)
        return when (val outcome = store.append(caller.householdId, op)) {
            is AppendOutcome.Stored -> BoardResult.Ok(BoardAck(scope, outcome.seq, outcome.duplicate))
            AppendOutcome.Conflict -> BoardResult.Failed(
                GatewayError("E_CONFLICT", "op id already stored with another body or scope"),
            )
        }
    }

    fun opsAfter(caller: DeviceIdentity, scope: String, after: Long, limit: Int? = null): BoardResult<BoardPage> {
        unknownScope(scope)?.let { return it }
        if (after < 0) return schema("after must not be negative")
        if (limit != null && limit < 1) return schema("limit must be positive")
        if (!mayRead(caller, scope)) return refused(scope)
        val pageSize = minOf(limit ?: maxPage, maxPage)
        val fetched = store.opsAfter(caller.householdId, scope, after, pageSize + 1)
        return BoardResult.Ok(BoardPage(fetched.take(pageSize), hasMore = fetched.size > pageSize))
    }

    /** The latest seq of every scope this device may read; 0 for a scope with no op yet. */
    fun head(caller: DeviceIdentity): Map<String, Long> {
        val latest = store.head(caller.householdId)
        return BoardScopes.ALL
            .filter { mayRead(caller, it) }
            .associateWith { latest[it] ?: 0L }
    }

    /** Records what this device has applied in [scope]. */
    fun putCursor(caller: DeviceIdentity, scope: String, seq: Long): BoardResult<Unit> {
        unknownScope(scope)?.let { return it }
        if (seq < 0) return schema("seq must not be negative")
        if (!mayRead(caller, scope)) return refused(scope)
        store.putCursor(caller.householdId, caller.deviceId, scope, seq)
        return BoardResult.Ok(Unit)
    }

    /** True when [caller] may read and write [scope] now. */
    fun mayRead(caller: DeviceIdentity, scope: String): Boolean = when (scope) {
        BoardScopes.FAMILY -> true
        BoardScopes.PARENTS -> store.heldEpoch(caller.householdId, caller.deviceId, scope) != null
        else -> false
    }

    private fun unknownScope(scope: String): BoardResult.Failed? =
        if (scope in BoardScopes.ALL) null else schema("unknown scope")

    private fun schema(message: String) = BoardResult.Failed(GatewayError("E_SCHEMA", message))

    private fun refused(scope: String) =
        BoardResult.Failed(GatewayError("E_SCOPE", "this device holds no current key for scope $scope"))

    private fun isUuid(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)

    companion object {
        /** Raw ciphertext cap, so a `board.op` frame stays under the 64 KiB frame cap. */
        const val MAX_CIPHERTEXT_BYTES = 44 * 1024

        /** IV (12) and GCM tag (16): the shortest well-formed ciphertext. */
        const val MIN_CIPHERTEXT_BYTES = 28

        const val MAX_PAGE = 500

        const val FAMILY_EPOCH = 0
    }
}
