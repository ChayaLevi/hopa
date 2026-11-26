package com.hopa.family.namespace

/**
 * The namespaces this build knows. `board` is always there: it is the board itself (v1 ops), read
 * by other namespaces but never registered here.
 */
class Namespaces(namespaces: List<Namespace> = emptyList()) {
    private val byName: Map<String, Namespace> = namespaces.associateBy { it.name }

    init {
        require(byName.size == namespaces.size) { "two namespaces share a name" }
        require(BOARD !in byName) { "board is not a registered namespace" }
        for (namespace in namespaces) {
            require(NAME.matches(namespace.name)) { "bad namespace name ${namespace.name}" }
            require(namespace.version >= 1) { "${namespace.name}: version must be 1 or more" }
            val known = namespace.reads.all { it == BOARD || it in byName }
            require(known) { "${namespace.name} reads an unknown namespace" }
            require(namespace.name !in namespace.reads) { "${namespace.name} reads itself" }
        }
    }

    operator fun get(name: String): Namespace? = byName[name]

    fun knows(name: String): Boolean = name == BOARD || name in byName

    /** Every namespace that reads [name], directly or through another one. */
    fun readersOf(name: String): Set<String> {
        val out = mutableSetOf<String>()
        var frontier = setOf(name)
        while (frontier.isNotEmpty()) {
            val next = byName.values
                .filter { ns -> ns.name !in out && ns.reads.any { it in frontier } }
                .map { it.name }
                .toSet()
            out += next
            frontier = next
        }
        return out
    }

    companion object {
        const val BOARD = "board"
        val NAME = Regex("^[a-z][a-z0-9_]{0,31}$")

        /** What this build ships. Capabilities are added here as C3 lands them. */
        val DEFAULT = Namespaces()
    }
}
