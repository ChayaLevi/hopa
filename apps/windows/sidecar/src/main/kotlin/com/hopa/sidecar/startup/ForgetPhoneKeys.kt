package com.hopa.sidecar.startup

import com.hopa.sidecar.store.PhoneKeys

/**
 * `--forget-phone-key` (ADR-0028): a phone paired again has a new signing
 * key, which this PC refuses until the old pin is gone. Prints what it did
 * on stderr and returns the exit code.
 */
internal fun forgetPhoneKeys(options: CliOptions): Int {
    val dataDir = options.dataDir ?: defaultDataDir()
    val forgotten = PhoneKeys(dataDir.resolve(PHONE_KEYS)).forgetAll()
    System.err.println("hopa-sidecar: forgot $forgotten pinned phone key(s); the next signed answer pins again")
    return 0
}
