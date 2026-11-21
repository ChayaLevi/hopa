package com.hopa.inference.local

/** What happened when bytes arrived for a model. */
sealed interface ImportOutcome {
    data class Imported(val model: InstalledModel) : ImportOutcome

    data class HashMismatch(val expected: String, val actual: String) : ImportOutcome

    data object TooLarge : ImportOutcome

    data object Empty : ImportOutcome

    /** The bytes did not arrive, or arrived in a state the store refuses. */
    data class Failed(val reason: ImportFailure) : ImportOutcome
}

/** Why a download or import stopped, in words a screen can explain. */
enum class ImportFailure {
    /** No connection, a timeout, or a server that refused the request. */
    NETWORK,

    /** The device ran out of space, or the app quota is full. */
    STORAGE,

    /** The file is not a GGUF model this engine can load. */
    UNSUPPORTED_FILE,

    /** The file is a GGUF but damaged or cut short. */
    CORRUPT,
}
