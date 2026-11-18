package com.hopa.engine.gate

import com.hopa.contracts.ApprovalGateway
import com.hopa.contracts.ApprovalRequest

class SessionApprovalGateway : ApprovalGateway {
    override suspend fun approve(request: ApprovalRequest): Boolean = !request.isPayment
}
