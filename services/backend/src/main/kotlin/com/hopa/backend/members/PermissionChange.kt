package com.hopa.backend.members

/**
 * A request to set [memberId]'s permissions to exactly [permissions]. The
 * caller's member comes from the access token, never from the request body.
 */
class PermissionChange(
    val callerMemberId: String,
    val memberId: String,
    val permissions: Set<String>,
)
