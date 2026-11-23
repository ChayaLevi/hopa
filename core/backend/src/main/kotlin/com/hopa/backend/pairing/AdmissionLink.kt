package com.hopa.backend.pairing

/**
 * The pending-admission QR a PC shows to a phone:
 * `hopa://pair?v=1&s=<secret>&u=<gateway>`. It carries no household: the secret
 * names a session that a signed-in phone binds to its own household when it
 * vouches. This is deliberately a different type from the operator link
 * ([PairingLink]), and an `h` parameter is refused rather than ignored.
 */
data class AdmissionLink(
    val session: String,
    val gatewayUrl: String,
)
