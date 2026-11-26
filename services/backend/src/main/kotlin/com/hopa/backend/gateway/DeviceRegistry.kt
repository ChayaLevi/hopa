package com.hopa.backend.gateway

import com.hopa.backend.auth.DeviceIdentity
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel

/**
 * Who is connected right now, and how to reach them.
 *
 * Presence is in memory on purpose: a socket only exists on the process that
 * holds it, and the durable record of a task is the Postgres row (slice B).
 */
class DeviceRegistry(private val clock: () -> Long = System::currentTimeMillis) {
    /**
     * One live socket. A plain class, not a data class: [sent] is mutated while
     * the session is a key in [live], so identity equality is required or the
     * set can never find the entry again.
     */
    class Session(
        val identity: DeviceIdentity,
        val outgoing: Channel<String>,
        /**
         * Task ids already sent on this socket. A reconnect creates a new
         * session, so a task the client never acked is still redelivered.
         */
        val sent: MutableSet<String> = ConcurrentHashMap.newKeySet(),
        /**
         * Completed by [kick]. The websocket handler awaits it and closes the
         * socket, so a revoked device loses the live connection immediately.
         */
        val closed: CompletableDeferred<Unit> = CompletableDeferred(),
    )

    private val sessions = ConcurrentHashMap<String, Session>()

    /**
     * Every open socket, hello or not. Presence ([sessions]) is announced only
     * on hello, but a revocation must also end a socket that connected just
     * before the device was cut off.
     */
    private val live = ConcurrentHashMap<String, MutableSet<Session>>()

    /**
     * Devices revoked in the last [KICKED_TTL_MS]. A revoke can land between
     * `deviceFor` resolving the token and the handler calling [attach]; without
     * this tombstone that reconnect would attach after [kick] and stay open
     * forever, because the handler never re-validates the token. The entry is
     * pruned lazily on the next attach/join/kick; five minutes far outlives the
     * five-second token cache and the revoke has already deleted the tokens.
     */
    private val kicked = ConcurrentHashMap<String, Long>()

    /** Makes the tombstone check+insert and [kick]'s write+sweep one step. */
    private val lock = Any()

    /** True while the device is present (hello received). */
    fun online(deviceId: String): Boolean =
        sessionFor(deviceId) != null

    fun sessionFor(deviceId: String): Session? = sessions[deviceId]

    /** True while any authenticated socket for the device is open, hello or not. */
    fun connected(deviceId: String): Boolean = live[deviceId]?.isNotEmpty() == true

    /** Presence-only join: a tombstoned device never becomes online. */
    fun join(session: Session): Boolean = synchronized(lock) {
        if (tombstoned(session.identity.deviceId)) {
            session.closed.complete(Unit)
            false
        } else {
            sessions[session.identity.deviceId] = session
            true
        }
    }

    fun leave(session: Session) {
        sessions.remove(session.identity.deviceId, session)
    }

    /**
     * Registers the socket as soon as it authenticates, before hello. Returns
     * false when a revoke already tombstoned the device.
     */
    fun attach(session: Session): Boolean = synchronized(lock) {
        if (tombstoned(session.identity.deviceId)) {
            session.closed.complete(Unit)
            false
        } else {
            live.computeIfAbsent(session.identity.deviceId) { ConcurrentHashMap.newKeySet() }
                .add(session)
            true
        }
    }

    fun detach(session: Session) {
        val device = session.identity.deviceId
        val set = live[device] ?: return
        set.remove(session)
        if (set.isEmpty()) live.remove(device, set)
    }

    /** Ends every live socket for the device; an offline device is a no-op. */
    fun kick(deviceId: String) {
        synchronized(lock) {
            val now = clock()
            pruneKicked(now)
            kicked[deviceId] = now + KICKED_TTL_MS
            live[deviceId]?.forEach { it.closed.complete(Unit) }
            sessions[deviceId]?.closed?.complete(Unit)
        }
    }

    /**
     * Ends every live socket for the device without a tombstone: the device is
     * still paired and may reconnect at once. Used when its rights change (a
     * family role change) so the client starts again from what it may read now.
     */
    fun disconnect(deviceId: String) {
        live[deviceId]?.forEach { it.closed.complete(Unit) }
        sessions[deviceId]?.closed?.complete(Unit)
    }

    fun householdSessions(householdId: String): List<Session> =
        sessions.values
            .filter { it.identity.householdId == householdId }
            .sortedBy { it.identity.deviceId }

    fun peers(householdId: String, exceptDeviceId: String): List<Session> =
        householdSessions(householdId).filter { it.identity.deviceId != exceptDeviceId }

    fun deviceIds(householdId: String): List<String> =
        householdSessions(householdId).map { it.identity.deviceId }

    private fun tombstoned(deviceId: String): Boolean {
        val now = clock()
        pruneKicked(now)
        val until = kicked[deviceId] ?: return false
        return until > now
    }

    private fun pruneKicked(now: Long) {
        kicked.entries.removeIf { (_, until) -> until <= now }
    }

    private companion object {
        /** Covers the auth→attach window for any later reconnect attempt. */
        const val KICKED_TTL_MS = 5 * 60 * 1000L
    }
}
