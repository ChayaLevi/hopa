package com.hopa.backend.board

/**
 * The household's board log. Every call is scoped to one household; the
 * scope rule (who may read `parents`) lives in [BoardService], not here.
 */
interface BoardStore {
    /**
     * Appends [op] at the household's next seq. Seqs of one household have no
     * gap and no duplicate. The same op id with the same body hash and scope
     * returns the stored seq as a duplicate and writes nothing; with another
     * body or scope it is a [AppendOutcome.Conflict].
     */
    fun append(householdId: String, op: NewBoardOp): AppendOutcome

    /** Up to [limit] ops of [scope] with a seq above [after], ascending. */
    fun opsAfter(householdId: String, scope: String, after: Long, limit: Int): List<BoardOp>

    /** The latest seq per scope that has any op. */
    fun head(householdId: String): Map<String, Long>

    fun putCursor(householdId: String, deviceId: String, scope: String, seq: Long)

    fun cursors(householdId: String, deviceId: String): Map<String, Long>

    fun putWrap(
        householdId: String,
        scope: String,
        keyEpoch: Int,
        deviceId: String,
        wrappedKey: ByteArray,
        wrapperDeviceId: String?,
    )

    /**
     * The current epoch of [scope] (the highest epoch with any wrap in the
     * household) when [deviceId] holds a wrap at it; null otherwise.
     */
    fun heldEpoch(householdId: String, deviceId: String, scope: String): Int?
}
