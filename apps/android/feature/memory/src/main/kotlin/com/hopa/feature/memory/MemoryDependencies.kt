package com.hopa.feature.memory

import com.hopa.stores.MemoryStore

/** The facts HOPA carries, and nothing else. */
interface MemoryDependencies {
    val memory: MemoryStore
}
