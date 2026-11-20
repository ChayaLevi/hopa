package com.hopa.inference.local

/** What happened when bytes arrived for a model. */
sealed interface ImportOutcome {
    data class Imported(val model: InstalledModel) : ImportOutcome

    data class HashMismatch(val expected: String, val actual: String) : ImportOutcome

    data object TooLarge : ImportOutcome

    data object Empty : ImportOutcome
}
