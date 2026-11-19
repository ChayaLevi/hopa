package com.hopa.catalog.store

/** Where a client gets its tables: the bundled snapshot today, a fetch later. */
interface CatalogSource {
    /** What this client can use right now. Never suspends: the copy is local. */
    fun current(): CatalogSnapshot
}
