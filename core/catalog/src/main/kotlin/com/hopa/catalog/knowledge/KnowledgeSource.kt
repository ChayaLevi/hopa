package com.hopa.catalog.knowledge

/** Where a client gets its tables: the bundled snapshot today, a fetch later. */
interface KnowledgeSource {
    /** What this client can use right now. Never suspends: the copy is local. */
    fun current(): Knowledge
}
