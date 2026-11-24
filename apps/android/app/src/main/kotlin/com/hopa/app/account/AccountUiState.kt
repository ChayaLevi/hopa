package com.hopa.app.account

/** The sign-in screen's state: one busy flag and one error, no phone/password split. */
data class AccountUiState(
    val busy: Boolean = false,
    val error: String? = null,
    val signedIn: Boolean = false,
)
