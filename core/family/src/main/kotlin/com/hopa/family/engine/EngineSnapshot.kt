package com.hopa.family.engine

import com.hopa.family.model.BoardState
import com.hopa.family.model.LogEntry
import com.hopa.family.model.Scope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** An op this device proposed and the server has not sent back in the log yet. */
@Serializable
data class PendingOp(
    val scope: Scope,
    val opId: String,
    val op: JsonObject,
)

/**
 * Everything one device keeps of the board between runs, stored on the device only. [engineVersion]
 * says how to read it ([EngineStore] migrates older versions and refuses newer ones, D15).
 */
@Serializable
data class EngineSnapshot(
    val engineVersion: Int,
    val memberId: String,
    val deviceId: String,
    /** The board as the server's log made it, op by op, in seq order. */
    val confirmed: BoardState,
    /** The last seq applied (or refused) per scope: the device's cursor. */
    val applied: Map<Scope, Long>,
    /** Local proposals, oldest first, not in the log yet. */
    val outbox: List<PendingOp>,
    /** The seq of an op this version cannot read; nothing from it on is applied (D15). */
    val stoppedAt: Long? = null,
    /** Entries waiting behind [stoppedAt], kept for the version that can read them. */
    val held: List<LogEntry> = emptyList(),
)
