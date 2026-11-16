package com.hopa.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** One finished turn: what was asked, where it ran, and what it cost. */
@Entity(tableName = "run_history")
data class RunHistoryEntity(
    @PrimaryKey val id: String,
    val at: Long,
    val goal: String,
    val path: String,
    val steps: Int,
    val tokens: Int,
    val conversationId: String,
)
