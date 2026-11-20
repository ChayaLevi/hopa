package com.hopa.feature.conversations

/** What a bridge chat sync moved, without the transport types. */
data class ChatSyncOutcome(
    val adopted: Int,
    val tombstoned: Int,
)
