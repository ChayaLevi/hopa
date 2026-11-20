package com.hopa.feature.settings

/** "1.1 GB" or "250 MB", the way a person reads a file size. */
fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000 -> "%.0f MB".format(bytes / 1_000_000.0)
    else -> "${bytes / 1_000} KB"
}
