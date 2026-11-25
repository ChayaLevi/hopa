package com.hopa.backend.board

/**
 * The board log for protocol tests. One lock per store keeps the seq and the
 * op-id index consistent, as the head row does in Postgres.
 */
class InMemoryBoardStore(
    private val clock: () -> Long = System::currentTimeMillis,
) : BoardStore {
    private class StoredOp(val op: BoardOp, val bodyHash: ByteArray)

    private class Wrap(val scope: String, val keyEpoch: Int, val deviceId: String)

    private val lock = Any()
    private val ops = mutableMapOf<String, MutableList<StoredOp>>()
    private val byOpId = mutableMapOf<String, StoredOp>()
    private val cursors = mutableMapOf<String, MutableMap<String, Long>>()
    private val wraps = mutableMapOf<String, MutableList<Wrap>>()

    override fun append(householdId: String, op: NewBoardOp): AppendOutcome = synchronized(lock) {
        val key = "$householdId/${op.opId}"
        byOpId[key]?.let { stored ->
            return@synchronized if (stored.op.scope == op.scope && stored.bodyHash.contentEquals(op.bodyHash)) {
                AppendOutcome.Stored(stored.op.seq, duplicate = true)
            } else {
                AppendOutcome.Conflict
            }
        }
        val log = ops.getOrPut(householdId) { mutableListOf() }
        val seq = log.size + 1L
        val stored = StoredOp(
            BoardOp(seq, op.scope, op.opId, op.fromDevice, op.memberId, op.keyEpoch, op.body.copyOf(), clock()),
            op.bodyHash,
        )
        log += stored
        byOpId[key] = stored
        AppendOutcome.Stored(seq, duplicate = false)
    }

    override fun opsAfter(householdId: String, scope: String, after: Long, limit: Int): List<BoardOp> =
        synchronized(lock) {
            ops[householdId].orEmpty()
                .asSequence()
                .map { it.op }
                .filter { it.scope == scope && it.seq > after }
                .take(limit)
                .toList()
        }

    override fun head(householdId: String): Map<String, Long> = synchronized(lock) {
        ops[householdId].orEmpty()
            .groupBy({ it.op.scope }, { it.op.seq })
            .mapValues { (_, seqs) -> seqs.max() }
    }

    override fun putCursor(householdId: String, deviceId: String, scope: String, seq: Long) {
        synchronized(lock) { cursors.getOrPut("$householdId/$deviceId") { mutableMapOf() }[scope] = seq }
    }

    override fun cursors(householdId: String, deviceId: String): Map<String, Long> = synchronized(lock) {
        cursors["$householdId/$deviceId"].orEmpty().toMap()
    }

    override fun putWrap(
        householdId: String,
        scope: String,
        keyEpoch: Int,
        deviceId: String,
        wrappedKey: ByteArray,
        wrapperDeviceId: String?,
    ) {
        synchronized(lock) {
            val list = wraps.getOrPut(householdId) { mutableListOf() }
            list.removeAll { it.scope == scope && it.keyEpoch == keyEpoch && it.deviceId == deviceId }
            list += Wrap(scope, keyEpoch, deviceId)
        }
    }

    override fun heldEpoch(householdId: String, deviceId: String, scope: String): Int? = synchronized(lock) {
        val inScope = wraps[householdId].orEmpty().filter { it.scope == scope }
        val current = inScope.maxOfOrNull { it.keyEpoch } ?: return@synchronized null
        current.takeIf { inScope.any { it.keyEpoch == current && it.deviceId == deviceId } }
    }
}
