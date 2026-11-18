package com.hopa.engine.execution

import com.hopa.contracts.approval.ApprovalRequest
import com.hopa.contracts.approval.PaymentContext
import com.hopa.contracts.approval.RiskLevel
import com.hopa.contracts.wire.Action
import com.hopa.contracts.wire.Observation
import com.hopa.contracts.wire.ObservationNode
import com.hopa.knowledge.ApprovalPolicyData

/**
 * The gate's settings for one run: whether the person turned autonomy on, and
 * which apps they trust.
 *
 * Both list and flag are fixed when the object is built. A turn reads a
 * snapshot; changing a setting builds a new one, so two runs in flight cannot
 * see each other's list half-applied. `withTrustedApps` and `withAutonomous`
 * are the way to change it.
 */
class ApprovalPolicy(
    private val classifier: ActionClassifier = ActionClassifier(),
    private val autonomous: Boolean = false,
    trustedApps: Collection<String> = emptyList(),
) {
    private val trusted: Set<String> = trustedApps
        .map { it.trim().lowercase() }
        .filter { it.isNotEmpty() }
        .toSet()

    fun withAutonomous(value: Boolean): ApprovalPolicy = ApprovalPolicy(classifier, value, trusted)

    fun withTrustedApps(apps: Collection<String>): ApprovalPolicy = ApprovalPolicy(classifier, autonomous, apps)

    fun isAutonomous(): Boolean = autonomous

    fun trustedApps(): Set<String> = trusted

    fun isTrustedApp(packageName: String): Boolean = trusted.contains(packageName.trim().lowercase())

    fun classify(action: Action, observation: Observation? = null): RiskLevel {
        val app = action.app?.trim()?.lowercase().orEmpty()
        if (
            action.kind in ApprovalPolicyData.TRUSTED_APP_KINDS &&
            app.isNotEmpty() &&
            trusted.contains(app)
        ) {
            return RiskLevel.READ
        }
        return classifier.classify(action, observation)
    }

    fun isPayment(action: Action, observation: Observation? = null): Boolean =
        classify(action, observation) == RiskLevel.PAYMENT

    fun requiresApproval(action: Action, observation: Observation? = null): Boolean {
        if (isPayment(action, observation)) {
            return true
        }
        if (autonomous) {
            return false
        }
        return classify(action, observation) != RiskLevel.READ
    }

    fun request(action: Action, observation: Observation? = null): ApprovalRequest {
        val payment = classifier.extractPaymentContext(observation)
        val isPayment = isPayment(action, observation)
        val title = if (isPayment) "Confirm payment" else "Allow this action?"
        val body = if (isPayment) {
            "Money may leave your account. HOPA cannot undo a payment."
        } else {
            "HOPA wants to ${action.kind.replace('.', ' ')} on this phone."
        }
        return ApprovalRequest(
            title = title,
            body = body,
            amount = payment.amount,
            merchant = payment.merchant,
            consequence = payment.consequence,
            isPayment = isPayment,
            action = action,
            highlight = highlightFor(observation, payment.amount),
            rememberPackage = if (isPayment) null else action.app?.takeIf { it.isNotBlank() },
        )
    }

    /** The bounds of the node that shows the amount, so the sheet can point at it. */
    private fun highlightFor(observation: Observation?, amount: String): List<Int>? {
        if (observation == null || amount.isBlank()) {
            return null
        }

        fun visit(node: ObservationNode): List<Int>? {
            val bounds = node.bounds
            if (bounds != null && bounds.size == 4 && node.text.contains(amount, ignoreCase = true)) {
                return bounds
            }
            return node.children.firstNotNullOfOrNull { visit(it) }
        }

        return visit(observation.tree)
    }

    fun extractPaymentContext(observation: Observation?): PaymentContext = classifier.extractPaymentContext(observation)
}
