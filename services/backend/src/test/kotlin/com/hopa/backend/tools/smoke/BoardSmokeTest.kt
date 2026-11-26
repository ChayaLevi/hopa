package com.hopa.backend.tools.smoke

import com.hopa.backend.ServerConfig
import com.hopa.backend.board.BoardHouseholds
import com.hopa.backend.harness.MapAuth
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.module
import com.hopa.backend.store.PgBoardStore
import com.hopa.backend.store.PgDevices
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoardSmokeTest {
    @Test
    fun `the smoke run appends and pulls over a real socket and reports percentiles`() {
        val db = PostgresHarness.appDatabase()
        val device = BoardHouseholds.seed(listOf("parent")).single()
        val config = ServerConfig(
            version = "0.1.0",
            auth = MapAuth(mapOf("tok-smoke" to device)),
            devices = PgDevices(db),
            boardStore = PgBoardStore(db),
        )
        val server = embeddedServer(Netty, port = 0) { module(config) }.start(wait = false)
        try {
            val port = runBlocking { server.engine.resolvedConnectors().first().port }

            val result = BoardSmoke.run("http://127.0.0.1:$port", "tok-smoke", ops = 25, pageSize = 10, paceMs = 60)

            assertEquals(25, result.appendMs.size)
            assertEquals(25, result.pulled)
            assertEquals(3, result.pullMs.size)
            assertTrue(result.appendP50 <= result.appendP95)
        } finally {
            server.stop(0, 0)
        }
    }

    @Test
    fun `percentiles use the nearest rank`() {
        val values = (1L..20L).toList()

        assertEquals(10L, BoardSmoke.percentile(values, 50))
        assertEquals(19L, BoardSmoke.percentile(values, 95))
        assertEquals(0L, BoardSmoke.percentile(emptyList(), 50))
    }
}
