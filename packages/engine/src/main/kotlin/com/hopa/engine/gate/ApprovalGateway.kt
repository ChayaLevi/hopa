package com.hopa.engine.gate

interface ApprovalGateway {
    suspend fun approve(request: ApprovalRequest): Boolean
}
