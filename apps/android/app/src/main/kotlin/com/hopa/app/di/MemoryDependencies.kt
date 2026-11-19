package com.hopa.app.di

import com.hopa.stores.MemoryStore

/** The facts HOPA carries, and nothing else. */
interface MemoryDependencies {
    val memory: MemoryStore
}
