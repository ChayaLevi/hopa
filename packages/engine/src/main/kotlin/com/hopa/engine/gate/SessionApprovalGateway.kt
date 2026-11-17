package com.hopa.engine.gate

class SessionApprovalGateway : ApprovalGateway {
    override suspend fun approve(request: ApprovalRequest): Boolean = !request.isPayment
}
