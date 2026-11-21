package com.hopa.inference.local

/**
 * How much of the phone the models may take.
 *
 * A person can install a 2.5 GB file three times and wonder why the camera has
 * no space. The quota is a ceiling for the whole model directory, checked before
 * every download and import; removal is always the person's decision.
 */
object LocalModelQuota {
    /** 8 GiB, enough for the largest offered model plus a spare. */
    const val MAX_BYTES: Long = 8L * 1024 * 1024 * 1024

    /** True when adding [bytes] to [used] would cross the quota. */
    fun exceeded(used: Long, bytes: Long, quota: Long = MAX_BYTES): Boolean =
        bytes > 0 && used + bytes > quota

    /** The number to show next to "of 8 GB". */
    fun remaining(used: Long, quota: Long = MAX_BYTES): Long = (quota - used).coerceAtLeast(0)
}
