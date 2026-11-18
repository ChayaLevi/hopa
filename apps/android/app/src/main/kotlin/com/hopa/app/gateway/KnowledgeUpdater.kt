package com.hopa.app.gateway

import com.hopa.knowledge.FileKnowledgeStore
import com.hopa.net.gateway.KnowledgeClient

/**
 * Asks the backend for a newer knowledge bundle, at most once per [intervalMs].
 *
 * It is deliberately quiet: a phone that never paired, a server that is down, a
 * 304 or a bundle the store refuses all leave the current copy in place. The
 * next turn reads whatever the store holds.
 */
class KnowledgeUpdater(
    private val session: BackendSession,
    private val store: FileKnowledgeStore,
    private val now: () -> Long = System::currentTimeMillis,
    private val intervalMs: Long = DEFAULT_INTERVAL_MS,
) {
    @Volatile
    private var lastCheck: Long = 0

    suspend fun refresh() {
        val stamp = now()
        if (stamp - lastCheck < intervalMs) {
            return
        }
        lastCheck = stamp
        val credentials = session.credentials() ?: return
        val baseUrl = credentials.gatewayUrl
            .replaceFirst("wss://", "https://")
            .replaceFirst("ws://", "http://")
            .substringBefore("/v1/")
        val text = runCatching {
            KnowledgeClient(baseUrl, credentials.accessToken)
                .fetch(store.current().version)
        }.getOrNull() ?: return
        store.accept(text)
    }

    private companion object {
        const val DEFAULT_INTERVAL_MS: Long = 6L * 60 * 60 * 1000
    }
}
