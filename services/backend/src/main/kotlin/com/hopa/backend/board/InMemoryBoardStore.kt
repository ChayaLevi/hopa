package com.hopa.backend.board

/**
 * The board log for protocol tests. One lock per store keeps the seq and the
 * op-id index consistent, as the head row does in Postgres, and serializes
 * wrap writes as the advisory lock does. Devices and family roles exist only
 * once [registerDevice] names them.
 */
class InMemoryBoardStore(
    private val clock: () -> Long = System::currentTimeMillis,
) : BoardStore {
    private class StoredOp(val op: BoardOp, val bodyHash: ByteArray)

    private class Wrap(
        val scope: String,
        val keyEpoch: Int,
        val deviceId: String,
        val wrappedKey: ByteArray,
        val wrapperDeviceId: String?,
    )

    private class Device(val householdId: String, val memberId: String, val publicKey: ByteArray?)

    private val lock = Any()
    private val ops = mutableMapOf<String, MutableList<StoredOp>>()
    private val byOpId = mutableMapOf<String, StoredOp>()
    private val cursors = mutableMapOf<String, MutableMap<String, Long>>()
    private val wraps = mutableMapOf<String, MutableList<Wrap>>()
    private val devices = mutableMapOf<String, Device>()
    private val roles = mutableMapOf<String, String?>()

    /** Makes a device and its member's family role known to the wrap rules. */
    fun registerDevice(householdId: String, deviceId: String, memberId: String, role: String?, publicKey: ByteArray? = null) {
        synchronized(lock) {
            devices[deviceId] = Device(householdId, memberId, publicKey)
            roles["$householdId/$memberId"] = role
        }
    }

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
            list += Wrap(scope, keyEpoch, deviceId, wrappedKey.copyOf(), wrapperDeviceId)
        }
    }

    override fun heldEpoch(householdId: String, deviceId: String, scope: String): Int? = synchronized(lock) {
        val inScope = wraps[householdId].orEmpty().filter { it.scope == scope }
        val current = inScope.maxOfOrNull { it.keyEpoch } ?: return@synchronized null
        current.takeIf { inScope.any { it.keyEpoch == current && it.deviceId == deviceId } }
    }

    override fun scopeKeyWrap(householdId: String, deviceId: String, scope: String): ScopeKeyWrap? = synchronized(lock) {
        val current = currentEpoch(householdId, scope) ?: return@synchronized null
        val wrap = wraps[householdId].orEmpty()
            .firstOrNull { it.scope == scope && it.keyEpoch == current && it.deviceId == deviceId }
            ?: return@synchronized null
        ScopeKeyWrap(
            scope,
            wrap.keyEpoch,
            wrap.wrappedKey.copyOf(),
            wrap.wrapperDeviceId,
            wrap.wrapperDeviceId?.let { devices[it]?.publicKey?.copyOf() },
        )
    }

    override fun scopeKeyState(householdId: String, scope: String): ScopeKeyState = synchronized(lock) {
        state(householdId, scope)
    }

    override fun writeWrap(householdId: String, request: WrapRequest): WrapOutcome = synchronized(lock) {
        val state = state(householdId, request.scope)
        val current = state.currentEpoch
        val snapshot = WrapSnapshot(
            callerRole = roleOf(householdId, request.callerDeviceId),
            targetExists = devices[request.targetDeviceId]?.householdId == householdId,
            targetRole = roleOf(householdId, request.targetDeviceId),
            state = state,
            callerHolds = current != null && wraps[householdId].orEmpty().any {
                it.scope == request.scope && it.keyEpoch == current && it.deviceId == request.callerDeviceId
            },
        )
        val outcome = ScopeKeyRules.decide(request, snapshot)
        if (outcome is WrapOutcome.Stored) {
            putWrap(householdId, request.scope, outcome.keyEpoch, request.targetDeviceId, request.wrappedKey, request.callerDeviceId)
        }
        outcome
    }

    override fun setFamilyRole(householdId: String, memberId: String, role: String?): List<String> = synchronized(lock) {
        val key = "$householdId/$memberId"
        if (!roles.containsKey(key)) return@synchronized emptyList()
        val previous = roles[key]
        roles[key] = role
        if (previous != ScopeKeyRules.PARENT || role == ScopeKeyRules.PARENT) return@synchronized emptyList()
        val memberDevices = devices.filter { (_, device) -> device.householdId == householdId && device.memberId == memberId }
            .keys.sorted()
        wraps[householdId]?.removeAll { it.scope == BoardScopes.PARENTS && it.deviceId in memberDevices }
        memberDevices
    }

    private fun currentEpoch(householdId: String, scope: String): Int? =
        wraps[householdId].orEmpty().filter { it.scope == scope }.maxOfOrNull { it.keyEpoch }

    private fun state(householdId: String, scope: String): ScopeKeyState {
        val current = currentEpoch(householdId, scope)
        val fromOps = ops[householdId].orEmpty().filter { it.op.scope == scope }.maxOfOrNull { it.op.keyEpoch } ?: 0
        return ScopeKeyState(current, maxOf(current ?: 0, fromOps))
    }

    private fun roleOf(householdId: String, deviceId: String): String? {
        val device = devices[deviceId]?.takeIf { it.householdId == householdId } ?: return null
        return roles["$householdId/${device.memberId}"]
    }
}
