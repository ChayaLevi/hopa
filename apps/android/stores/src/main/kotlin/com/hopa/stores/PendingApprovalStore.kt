package com.hopa.stores

import com.hopa.contracts.approval.ApprovalRequest

/** Survives a process death, so a question is never silently lost. */
interface PendingApprovalStore {
    suspend fun save(request: ApprovalRequest)

    suspend fun clear()

    suspend fun clearAll()

    suspend fun all(): List<ApprovalRequest>
}
