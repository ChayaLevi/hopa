package com.hopa.app.link.sync

data class ChatSyncReport(
    val rev: Int,
    val adopted: Int,
    val tombstoned: Int,
    val pulledFallback: Boolean,
)
