package com.hopa.contracts.approval

interface ApprovalGateway {
    suspend fun approve(request: ApprovalRequest): Boolean
}
