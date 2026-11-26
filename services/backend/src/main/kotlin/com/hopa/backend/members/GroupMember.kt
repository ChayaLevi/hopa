package com.hopa.backend.members

import kotlinx.serialization.Serializable

/**
 * One member as a device of the same group sees them: the display name, the
 * permissions (sorted) and whether an account stands behind the member (a small
 * child may have none).
 */
@Serializable
data class GroupMember(
    val id: String,
    val displayName: String,
    val permissions: List<String>,
    val hasAccount: Boolean,
)
