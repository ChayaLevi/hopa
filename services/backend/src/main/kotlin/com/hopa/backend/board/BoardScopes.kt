package com.hopa.backend.board

/**
 * The two board scopes (ADR-0031). `family` is read with the household key;
 * `parents` only by a device holding a wrap of the parents key at the current
 * epoch.
 */
object BoardScopes {
    const val FAMILY = "family"
    const val PARENTS = "parents"

    val ALL: List<String> = listOf(FAMILY, PARENTS)
}
