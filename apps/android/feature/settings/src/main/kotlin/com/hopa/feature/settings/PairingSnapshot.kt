package com.hopa.feature.settings

/** The gateway and bridge fields the settings pages show. */
internal data class PairingSnapshot(
    val backendUrl: String,
    val backendDeviceId: String,
    val backendName: String,
    val approvalKeyFingerprint: String,
    val backendToken: String?,
    val fcmAvailable: Boolean,
    val bridgeKey: String,
)
