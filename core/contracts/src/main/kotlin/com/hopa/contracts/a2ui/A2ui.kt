package com.hopa.contracts.a2ui

/**
 * HOPA's pinned A2UI contract (ADR-0031): the wire version, the catalog every
 * HOPA card uses, and the limits a receiver enforces. `spec/a2ui/` holds the
 * same values as JSON; `tools/checks/check-a2ui.py` keeps the two in step.
 */
object A2ui {
    /** The exact `version` value of every message; `"v1.0"` is not it. */
    const val VERSION = "1.0"
    const val CATALOG_ID = "https://hopa.dev/contracts/a2ui/hopa-catalog.v1.json"

    const val MAX_COMPONENTS = 64
    const val MAX_CHILDREN = 16
    const val MAX_DEPTH = 8
    const val MAX_TEXT = 1000
    const val MAX_PATH = 256
    const val MAX_CONTEXT = 8
    const val MAX_MESSAGE_CHARS = 32_768

    internal val ID = Regex("^[A-Za-z_][A-Za-z0-9_]{0,63}$")
    internal val SURFACE_ID = Regex("^[A-Za-z0-9_.:-]{1,128}$")
    internal val EVENT_NAME = Regex("^[a-z][A-Za-z]{0,31}$")
    internal val CONTEXT_KEY = Regex("^[A-Za-z_][A-Za-z0-9_]{0,31}$")

    /**
     * A surface id that is unique for a renderer's lifetime when [scope] is
     * (a task or turn id) and [n] counts the questions inside it.
     */
    fun surfaceId(prefix: String, scope: String, n: Int): String {
        val clean = scope.replace(Regex("[^A-Za-z0-9]"), "_").take(100)
        return "${prefix}_${clean}_$n"
    }
}
