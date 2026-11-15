package com.hopa.data.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A question that was on screen when the process died. */
@Entity(tableName = "pending_approvals")
data class PendingApprovalEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String,
    val amount: String,
    val merchant: String,
    val consequence: String,
    val isPayment: Boolean,
    val actionJson: String,
    val createdAt: Long,
    /** [left, top, right, bottom] of the amount, empty when unknown. */
    val highlightJson: String = "",
    /** The app a "remember this app" answer applies to, empty when none. */
    val rememberPackage: String = "",
)
