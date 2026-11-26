package com.hopa.family.scope

/**
 * The op id and time a new op carries (D10). The helpers here never read a clock or mint an id:
 * the caller (the engine on the device) supplies them, so the helpers stay pure and testable.
 */
data class OpStamp(
    val opId: String,
    val at: String,
)

/** Gives the stamp for each op a helper writes, in the order it writes them. */
fun interface OpStamps {
    fun next(): OpStamp
}

/** Gives the id of the item a helper creates in place of [oldId]. Ids must not repeat (D10). */
fun interface IdMint {
    fun idFor(oldId: String): String
}
