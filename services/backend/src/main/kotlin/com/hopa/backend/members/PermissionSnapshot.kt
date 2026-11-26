package com.hopa.backend.members

/**
 * What [PermissionRules] need, read under the group's lock in the same
 * transaction as the write. [target] is null when the member is not in the
 * group; [managers] counts the members holding [Permissions.GROUP_MANAGE].
 */
class PermissionSnapshot(
    val caller: Set<String>,
    val target: Set<String>?,
    val managers: Int,
)
