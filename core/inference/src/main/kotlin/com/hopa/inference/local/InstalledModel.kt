package com.hopa.inference.local

import java.io.File

/** One model file on disk, as the storage sees it. */
data class InstalledModel(
    val id: String,
    val file: File,
    val bytes: Long,
)
