package com.hopa.agent.gate

import com.hopa.contracts.approval.RiskLevel
import com.hopa.contracts.wire.Action
import com.hopa.contracts.wire.Observation
import com.hopa.contracts.wire.ObservationNode
import com.hopa.contracts.wire.ScreenSize
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApprovalPolicyTest {
    private val policy = ApprovalPolicy()

    private fun screen(text: String): Observation = Observation(
        id = "obs-test",
        timestamp = 1,
        screenSize = ScreenSize(1080, 2400),
        text = text,
        tree = ObservationNode(type = "root"),
    )

    @Test
    fun payment_kind_always_asks() {
        val action = Action(kind = "payment.checkout")
        assertEquals(RiskLevel.PAYMENT, policy.classify(action))
        assertTrue(policy.requiresApproval(action))
    }

    @Test
    fun a_tap_on_a_payment_screen_asks() {
        val action = Action(kind = "ui.tap", ref = "e2")
        val observation = screen("Checkout\nTotal Â¥19.99\nPay now")
        assertEquals(RiskLevel.PAYMENT, policy.classify(action, observation))
        assertTrue(policy.requiresApproval(action, observation))
    }

    @Test
    fun a_read_action_on_a_payment_screen_does_not_ask() {
        val action = Action(kind = "web.navigate", url = "https://example.com")
        val observation = screen("Checkout\nTotal Â¥19.99\nPay now")
        assertEquals(RiskLevel.READ, policy.classify(action, observation))
        assertFalse(policy.requiresApproval(action, observation))
    }

    @Test
    fun sensitive_and_unknown_actions_ask() {
        assertTrue(policy.requiresApproval(Action(kind = "system.delete")))
        assertTrue(policy.requiresApproval(Action(kind = "custom.unknown")))
    }

    @Test
    fun a_trusted_app_skips_the_message_prompt() {
        val policy = ApprovalPolicy().withTrustedApps(listOf("com.example.chat"))
        val action = Action(kind = "system.sendMessage", app = "com.example.chat")
        assertEquals(RiskLevel.READ, policy.classify(action))
        assertFalse(policy.requiresApproval(action))
    }

    @Test
    fun trust_covers_typed_calls_and_closing_but_never_payments() {
        val policy = ApprovalPolicy().withTrustedApps(listOf("com.example.maps"))
        assertFalse(
            policy.requiresApproval(
                Action(kind = "app.function", app = "com.example.maps"),
            ),
        )
        assertFalse(
            policy.requiresApproval(Action(kind = "ui.stop", app = "com.example.maps")),
        )
        // A payment in a trusted app still asks.
        assertTrue(
            policy.requiresApproval(
                Action(kind = "payment.pay", app = "com.example.maps"),
            ),
        )
        // Uncatalogued sensitive device actions are not app-scoped.
        assertTrue(
            policy.requiresApproval(
                Action(kind = "system.uninstall", app = "com.example.maps"),
            ),
        )
    }

    @Test
    fun extracts_amount_and_merchant() {
        val context = policy.extractPaymentContext(
            screen("Merchant: Blue Cafe\nAmount \$4.50"),
        )
        assertEquals("\$4.50", context.amount)
        assertEquals("Blue Cafe", context.merchant)
    }
}
