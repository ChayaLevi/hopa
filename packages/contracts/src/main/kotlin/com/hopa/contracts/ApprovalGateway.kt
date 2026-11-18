package com.hopa.contracts

interface ApprovalGateway {
    suspend fun approve(request: ApprovalRequest): Boolean
}
