package com.hopa.backend.board

import com.hopa.backend.auth.DeviceIdentity
import java.util.UUID

class InMemoryBoardStoreTest : BoardStoreContract() {
    override val store: BoardStore = InMemoryBoardStore()

    override fun reopened(): BoardStore = store

    override fun household(devices: Int): List<DeviceIdentity> {
        val household = UUID.randomUUID().toString()
        return List(devices) {
            DeviceIdentity(UUID.randomUUID().toString(), household, UUID.randomUUID().toString())
        }
    }
}
