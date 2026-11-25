package com.hopa.backend.board

import com.hopa.backend.auth.DeviceIdentity
import com.hopa.backend.harness.PostgresHarness
import com.hopa.backend.store.PgBoardStore

class PgBoardStoreTest : BoardStoreContract() {
    private val db = PostgresHarness.appDatabase()

    override val store: BoardStore = PgBoardStore(db)

    /** A second store over the same database: what a reconnect or a restart sees. */
    override fun reopened(): BoardStore = PgBoardStore(db)

    override fun household(devices: Int): List<DeviceIdentity> = BoardHouseholds.seed(List(devices) { null })
}
