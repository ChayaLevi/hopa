package com.hopa.backend.store

import com.hopa.backend.board.AppendOutcome
import com.hopa.backend.board.BoardOp
import com.hopa.backend.board.BoardStore
import com.hopa.backend.board.NewBoardOp
import com.hopa.backend.board.ScopeKeyRules
import com.hopa.backend.board.ScopeKeyState
import com.hopa.backend.board.ScopeKeyWrap
import com.hopa.backend.board.WrapOutcome
import com.hopa.backend.board.WrapRequest
import com.hopa.backend.board.WrapSnapshot
import java.sql.Connection
import java.sql.Timestamp
import java.time.Instant

/**
 * The board log in Postgres (V6). Every statement runs in a household
 * transaction, so the policies decide what this repository can see.
 *
 * An append first bumps `board_heads` with one upsert. That takes the row lock
 * every other append of the household waits on, so seqs are handed out one at a
 * time and the op-id check that follows sees every op committed before it. A
 * reused op id rolls back to a savepoint taken before the bump, so a duplicate
 * or a conflict never burns a seq.
 */
class PgBoardStore(
    private val db: Database,
    private val clock: () -> Long = System::currentTimeMillis,
) : BoardStore {
    override fun append(householdId: String, op: NewBoardOp): AppendOutcome = db.tenant(householdId) { connection ->
        val beforeBump = connection.setSavepoint()
        val seq = nextSeq(connection, householdId)
        val existing = findByOpId(connection, householdId, op.opId)
        if (existing != null) {
            connection.rollback(beforeBump)
            return@tenant if (existing.scope == op.scope && existing.bodyHash.contentEquals(op.bodyHash)) {
                AppendOutcome.Stored(existing.seq, duplicate = true)
            } else {
                AppendOutcome.Conflict
            }
        }
        connection.prepareStatement(
            """
            insert into board_ops(household_id, seq, scope, op_id, from_device, member_id,
                                  key_epoch, body, body_hash, size, created_at)
            values (?::uuid, ?, ?, ?::uuid, ?::uuid, ?::uuid, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use {
            it.setString(1, householdId)
            it.setLong(2, seq)
            it.setString(3, op.scope)
            it.setString(4, op.opId)
            it.setString(5, op.fromDevice)
            it.setString(6, op.memberId)
            it.setInt(7, op.keyEpoch)
            it.setBytes(8, op.body)
            it.setBytes(9, op.bodyHash)
            it.setInt(10, op.body.size)
            it.setTimestamp(11, timestamp(clock()))
            it.executeUpdate()
        }
        AppendOutcome.Stored(seq, duplicate = false)
    }

    override fun opsAfter(householdId: String, scope: String, after: Long, limit: Int): List<BoardOp> =
        db.tenant(householdId) { connection ->
            connection.prepareStatement(
                """
                select seq, scope, op_id, from_device, member_id, key_epoch, body, created_at
                from board_ops
                where household_id = ?::uuid and scope = ? and seq > ?
                order by seq
                limit ?
                """.trimIndent(),
            ).use {
                it.setString(1, householdId)
                it.setString(2, scope)
                it.setLong(3, after)
                it.setInt(4, limit)
                it.executeQuery().use { rows ->
                    buildList {
                        while (rows.next()) {
                            add(
                                BoardOp(
                                    seq = rows.getLong(1),
                                    scope = rows.getString(2),
                                    opId = rows.getString(3),
                                    fromDevice = rows.getString(4),
                                    memberId = rows.getString(5),
                                    keyEpoch = rows.getInt(6),
                                    body = rows.getBytes(7),
                                    createdAt = rows.getTimestamp(8).time,
                                ),
                            )
                        }
                    }
                }
            }
        }

    override fun head(householdId: String): Map<String, Long> = db.tenant(householdId) { connection ->
        connection.prepareStatement(
            "select scope, max(seq) from board_ops where household_id = ?::uuid group by scope",
        ).use {
            it.setString(1, householdId)
            it.executeQuery().use { rows ->
                buildMap { while (rows.next()) put(rows.getString(1), rows.getLong(2)) }
            }
        }
    }

    override fun putCursor(householdId: String, deviceId: String, scope: String, seq: Long) {
        db.tenant(householdId) { connection ->
            connection.prepareStatement(
                """
                insert into board_cursors(household_id, device_id, scope, last_seq, updated_at)
                values (?::uuid, ?::uuid, ?, ?, ?)
                on conflict (device_id, scope) do update
                  set last_seq = excluded.last_seq, updated_at = excluded.updated_at
                """.trimIndent(),
            ).use {
                it.setString(1, householdId)
                it.setString(2, deviceId)
                it.setString(3, scope)
                it.setLong(4, seq)
                it.setTimestamp(5, timestamp(clock()))
                it.executeUpdate()
            }
        }
    }

    override fun cursors(householdId: String, deviceId: String): Map<String, Long> =
        db.tenant(householdId) { connection ->
            connection.prepareStatement(
                "select scope, last_seq from board_cursors where household_id = ?::uuid and device_id = ?::uuid",
            ).use {
                it.setString(1, householdId)
                it.setString(2, deviceId)
                it.executeQuery().use { rows ->
                    buildMap { while (rows.next()) put(rows.getString(1), rows.getLong(2)) }
                }
            }
        }

    override fun putWrap(
        householdId: String,
        scope: String,
        keyEpoch: Int,
        deviceId: String,
        wrappedKey: ByteArray,
        wrapperDeviceId: String?,
    ) {
        db.tenant(householdId) { connection ->
            insertWrap(connection, householdId, scope, keyEpoch, deviceId, wrappedKey, wrapperDeviceId)
        }
    }

    override fun heldEpoch(householdId: String, deviceId: String, scope: String): Int? =
        db.tenant(householdId) { connection ->
            connection.prepareStatement(
                """
                select w.key_epoch
                from scope_key_wraps w
                where w.household_id = ?::uuid and w.scope = ? and w.device_id = ?::uuid
                  and w.key_epoch = (
                    select max(key_epoch) from scope_key_wraps
                    where household_id = ?::uuid and scope = ?
                  )
                """.trimIndent(),
            ).use {
                it.setString(1, householdId)
                it.setString(2, scope)
                it.setString(3, deviceId)
                it.setString(4, householdId)
                it.setString(5, scope)
                it.executeQuery().use { rows -> if (rows.next()) rows.getInt(1) else null }
            }
        }

    override fun scopeKeyWrap(householdId: String, deviceId: String, scope: String): ScopeKeyWrap? =
        db.tenant(householdId) { connection ->
            connection.prepareStatement(
                """
                select w.key_epoch, w.wrapped_key, w.wrapper_device_id, d.public_key
                from scope_key_wraps w
                left join devices d on d.id = w.wrapper_device_id
                where w.household_id = ?::uuid and w.scope = ? and w.device_id = ?::uuid
                  and w.key_epoch = (
                    select max(key_epoch) from scope_key_wraps
                    where household_id = ?::uuid and scope = ?
                  )
                """.trimIndent(),
            ).use {
                it.setString(1, householdId)
                it.setString(2, scope)
                it.setString(3, deviceId)
                it.setString(4, householdId)
                it.setString(5, scope)
                it.executeQuery().use { rows ->
                    if (!rows.next()) {
                        null
                    } else {
                        ScopeKeyWrap(scope, rows.getInt(1), rows.getBytes(2), rows.getString(3), rows.getBytes(4))
                    }
                }
            }
        }

    override fun scopeKeyState(householdId: String, scope: String): ScopeKeyState =
        db.tenant(householdId) { connection -> state(connection, householdId, scope) }

    override fun writeWrap(householdId: String, request: WrapRequest): WrapOutcome = db.tenant(householdId) { connection ->
        // One writer per household and scope until commit: the bootstrap check
        // and the insert that follows cannot interleave with another one, nor
        // with a permission change dropping the target's wraps.
        AdvisoryLocks.scopeKeyWraps(connection, householdId, request.scope)
        val state = state(connection, householdId, request.scope)
        val caller = privateOf(connection, householdId, request.callerDeviceId)
        val target = privateOf(connection, householdId, request.targetDeviceId)
        val current = state.currentEpoch
        val callerHolds = current != null && holds(connection, householdId, request.callerDeviceId, request.scope, current)
        val snapshot = WrapSnapshot(
            callerPrivate = caller == true,
            targetExists = target != null,
            targetPrivate = target == true,
            state = state,
            callerHolds = callerHolds,
        )
        val outcome = ScopeKeyRules.decide(request, snapshot)
        if (outcome is WrapOutcome.Stored) {
            insertWrap(
                connection,
                householdId,
                request.scope,
                outcome.keyEpoch,
                request.targetDeviceId,
                request.wrappedKey,
                request.callerDeviceId,
            )
        }
        outcome
    }

    private fun state(connection: Connection, householdId: String, scope: String): ScopeKeyState =
        connection.prepareStatement(
            """
            select (select max(key_epoch) from scope_key_wraps where household_id = ?::uuid and scope = ?),
                   (select max(key_epoch) from board_ops where household_id = ?::uuid and scope = ?)
            """.trimIndent(),
        ).use {
            it.setString(1, householdId)
            it.setString(2, scope)
            it.setString(3, householdId)
            it.setString(4, scope)
            it.executeQuery().use { rows ->
                rows.next()
                val current = rows.getInt(1).takeUnless { rows.wasNull() }
                val fromOps = rows.getInt(2).takeUnless { rows.wasNull() } ?: 0
                ScopeKeyState(current, maxOf(current ?: 0, fromOps))
            }
        }

    /**
     * Whether a live device's member holds `board.private`; null when the
     * device is not live in the household.
     */
    private fun privateOf(connection: Connection, householdId: String, deviceId: String): Boolean? =
        connection.prepareStatement(
            """
            select exists (
                select 1 from member_permissions p where p.member_id = d.member_id and p.permission = 'board.private'
            )
            from devices d
            where d.id = ?::uuid and d.household_id = ?::uuid and d.revoked_at is null
            """.trimIndent(),
        ).use {
            it.setString(1, deviceId)
            it.setString(2, householdId)
            it.executeQuery().use { rows -> if (rows.next()) rows.getBoolean(1) else null }
        }

    private fun holds(connection: Connection, householdId: String, deviceId: String, scope: String, epoch: Int): Boolean =
        connection.prepareStatement(
            "select 1 from scope_key_wraps " +
                "where household_id = ?::uuid and device_id = ?::uuid and scope = ? and key_epoch = ?",
        ).use {
            it.setString(1, householdId)
            it.setString(2, deviceId)
            it.setString(3, scope)
            it.setInt(4, epoch)
            it.executeQuery().use { rows -> rows.next() }
        }

    private fun insertWrap(
        connection: Connection,
        householdId: String,
        scope: String,
        keyEpoch: Int,
        deviceId: String,
        wrappedKey: ByteArray,
        wrapperDeviceId: String?,
    ) {
        connection.prepareStatement(
            """
            insert into scope_key_wraps(household_id, scope, key_epoch, device_id, wrapped_key,
                                        wrapper_device_id, wrapped_at)
            values (?::uuid, ?, ?, ?::uuid, ?, ?::uuid, ?)
            on conflict (device_id, scope, key_epoch) do update
              set wrapped_key = excluded.wrapped_key,
                  wrapper_device_id = excluded.wrapper_device_id,
                  wrapped_at = excluded.wrapped_at
            """.trimIndent(),
        ).use {
            it.setString(1, householdId)
            it.setString(2, scope)
            it.setInt(3, keyEpoch)
            it.setString(4, deviceId)
            it.setBytes(5, wrappedKey)
            it.setString(6, wrapperDeviceId)
            it.setTimestamp(7, timestamp(clock()))
            it.executeUpdate()
        }
    }

    private class Existing(val seq: Long, val scope: String, val bodyHash: ByteArray)

    private fun nextSeq(connection: Connection, householdId: String): Long =
        connection.prepareStatement(
            """
            insert into board_heads(household_id, last_seq) values (?::uuid, 1)
            on conflict (household_id) do update set last_seq = board_heads.last_seq + 1
            returning last_seq
            """.trimIndent(),
        ).use {
            it.setString(1, householdId)
            it.executeQuery().use { rows ->
                rows.next()
                rows.getLong(1)
            }
        }

    private fun findByOpId(connection: Connection, householdId: String, opId: String): Existing? =
        connection.prepareStatement(
            "select seq, scope, body_hash from board_ops where household_id = ?::uuid and op_id = ?::uuid",
        ).use {
            it.setString(1, householdId)
            it.setString(2, opId)
            it.executeQuery().use { rows ->
                if (rows.next()) Existing(rows.getLong(1), rows.getString(2), rows.getBytes(3)) else null
            }
        }

    private fun timestamp(millis: Long): Timestamp = Timestamp.from(Instant.ofEpochMilli(millis))
}
