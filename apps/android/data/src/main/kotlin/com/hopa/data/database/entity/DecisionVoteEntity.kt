package com.hopa.data.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One layer's answer at one step. A failed layer is a row too, so a broken
 * adapter cannot look better than a working one in the comparison.
 */
@Entity(tableName = "decision_votes", indices = [Index("runId")])
data class DecisionVoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val runId: String,
    val step: Int,
    val layerId: String,
    val layerVersion: String,
    val actionKind: String,
    val actionClass: String,
    val mode: String,
    val verb: String?,
    val reason: String,
    val confidence: Double,
    val trace: String,
    val latencyMicros: Long,
    val applied: Boolean,
    val error: String?,
    val notice: String?,
    val at: Long,
)
