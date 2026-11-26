package com.hopa.backend.members

/**
 * What a member may do in their group (V7, ADR-0033 amendment). A household is a
 * group; a family is one kind of group. Clients show a member with
 * [BOARD_PRIVATE] as a parent and one without it as a child: the server stores
 * permissions, never those words.
 */
object Permissions {
    /** Read and write the private board scope (`parents`) and hold its key. */
    const val BOARD_PRIVATE = "board.private"

    /** Change the members' permissions. */
    const val GROUP_MANAGE = "group.manage"

    val ALL: Set<String> = setOf(BOARD_PRIVATE, GROUP_MANAGE)

    /** What the member who creates a household gets. */
    val FOUNDER: Set<String> = ALL
}
