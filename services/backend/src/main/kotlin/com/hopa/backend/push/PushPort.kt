package com.hopa.backend.push

enum class PushOutcome { Delivered, InvalidToken, Retryable, Skipped }

/** The FCM call itself, without rate limits or deduplication. */
fun interface PushSender {
    fun send(token: String, data: Map<String, String>): PushOutcome
}

/** A wake for one offline device. The server never puts message content here. */
fun interface PushPort {
    fun wake(deviceId: String, taskId: String, traceId: String, pushToken: String): PushOutcome

    /**
     * A family board wake (ADR-0033): the household log has an op up to [seq]
     * in [scope] that this offline device may read. Only the scope and the seq
     * travel; the device pulls the ops itself. Board wakes have their own
     * budget and never spend the task wakes'.
     */
    fun wakeBoard(deviceId: String, scope: String, seq: Long, pushToken: String): PushOutcome = PushOutcome.Skipped

    /** Sends board wakes held back by coalescing whose window has passed; how many were sent. */
    fun flushBoardWakes(): Int = 0

    companion object {
        val None = PushPort { _, _, _, _ -> PushOutcome.Skipped }
    }
}
