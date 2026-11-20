package com.hopa.feature.settings

import com.hopa.inference.local.InstalledModel
import com.hopa.inference.local.ModelSpec

/** What the on-device section shows: the offer, the files, and the outcome. */
data class LocalModelUiState(
    val recommended: ModelSpec? = null,
    val installed: List<InstalledModel> = emptyList(),
    val installedSpecs: Map<String, ModelSpec> = emptyMap(),
    val totalBytes: Long = 0,
    val ramGb: Int = 0,
    val busy: Boolean = false,
    val downloadingId: String? = null,
    val downloadProgress: Float? = null,
    val notice: String? = null,
    val error: String? = null,
)
