package com.hopa.app.pair

/** The PC a scan found, as the phone will show it before vouching. */
data class PendingConfirm(
    val name: String,
    val fingerprint: String,
)

/** The scan-and-vouch screen: a scan state, a confirm state, or a refusal. */
data class VouchUiState(
    val busy: Boolean = false,
    val confirm: PendingConfirm? = null,
    val error: String? = null,
    val vouched: Boolean = false,
)
