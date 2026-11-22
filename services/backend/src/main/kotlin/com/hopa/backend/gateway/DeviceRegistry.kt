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
class DeviceRegistry {
    data class Session(
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

    fun join(session: Session) {
        sessions[session.identity.deviceId] = session
    }

    fun leave(session: Session) {
        sessions.remove(session.identity.deviceId, session)
    }

    /** Registers the socket as soon as it authenticates, before hello. */
    fun attach(session: Session) {
        live.computeIfAbsent(session.identity.deviceId) { ConcurrentHashMap.newKeySet() }
            .add(session)
    }

    fun detach(session: Session) {
        val device = session.identity.deviceId
        val set = live[device] ?: return
        set.remove(session)
        if (set.isEmpty()) live.remove(device, set)
    }

    fun online(deviceId: String): Boolean = sessions.containsKey(deviceId)

    fun sessionFor(deviceId: String): Session? = sessions[deviceId]

    /** Ends every live socket for the device; an offline device is a no-op. */
    fun kick(deviceId: String) {
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
}
