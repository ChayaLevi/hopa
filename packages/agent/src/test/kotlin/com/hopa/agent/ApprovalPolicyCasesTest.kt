package com.hopa.agent

import com.hopa.catalog.ApprovalPolicyData
import com.hopa.contracts.approval.RiskLevel
import com.hopa.contracts.wire.Action
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** One assertion per policy list, so a generated list cannot drift unnoticed. */
class ApprovalPolicyCasesTest {
    private val policy = ApprovalPolicy()

    @Test
    fun every_payment_kind_is_a_payment_and_always_asks() {
        for (kind in ApprovalPolicyData.PAYMENT_KINDS) {
            val action = Action(kind = kind)
            assertEquals(RiskLevel.PAYMENT, policy.classify(action), kind)
            assertTrue(policy.requiresApproval(action), kind)
        }
    }

    @Test
    fun an_autonomous_run_still_asks_for_a_payment() {
        val autonomous = ApprovalPolicy(autonomous = true)
        val payment = Action(kind = ApprovalPolicyData.PAYMENT_KINDS.first())
        assertTrue(autonomous.requiresApproval(payment))
        val allowed = Action(kind = ApprovalPolicyData.ALLOWED_KINDS.first())
        assertFalse(autonomous.requiresApproval(allowed))
    }

    @Test
    fun every_sensitive_kind_asks() {
        for (kind in ApprovalPolicyData.SENSITIVE_KINDS) {
            assertTrue(policy.requiresApproval(Action(kind = kind)), kind)
            assertTrue(policy.classify(Action(kind = kind)) != RiskLevel.READ, kind)
        }
    }

    @Test
    fun every_allowed_kind_runs_without_asking() {
        for (kind in ApprovalPolicyData.ALLOWED_KINDS) {
            assertFalse(policy.requiresApproval(Action(kind = kind)), kind)
        }
    }

    @Test
    fun an_unknown_kind_fails_closed() {
        val action = Action(kind = "ui.teleport")
        assertTrue(policy.requiresApproval(action))
        assertEquals(RiskLevel.SENSITIVE, policy.classify(action))
    }
}
